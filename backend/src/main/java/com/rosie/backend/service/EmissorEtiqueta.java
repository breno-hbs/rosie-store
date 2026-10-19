package com.rosie.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Etiqueta de envio (RF12, #25). Em produção é o Melhor Envio; nos testes, uma implementação falsa, para o
// CI não depender do token nem gastar saldo. Cada passo é uma chamada separada porque comprar debita o
// saldo da conta da Roseane: o EtiquetaService grava o resultado de cada um e, se algo falhar no meio,
// retoma de onde parou em vez de comprar de novo.
//
// Erros: EtiquetaIndisponivelException (sem configuração, token recusado) ou EtiquetaFalhouException
// (envio recusado, saldo insuficiente, serviço fora do ar, resposta inesperada).
public interface EmissorEtiqueta {

    // Põe o envio no carrinho do Melhor Envio e devolve o id dele. Ainda não cobra nada.
    String adicionarAoCarrinho(NovoEnvio envio);

    // Em que pé o envio está, o código de rastreio, se já existir, e se a transportadora já entregou (#64)
    SituacaoEnvio consultar(String envioId);

    // Paga o envio com o saldo da conta do Melhor Envio
    void comprar(String envioId);

    // Gera a etiqueta de um envio já pago
    void gerar(String envioId);

    // Link para abrir e imprimir a etiqueta gerada
    String linkImpressao(String envioId);

    // false quando o servidor não tem o Melhor Envio configurado (sem MELHOR_ENVIO_TOKEN): a rotina do rastreio
    // (#64) nem tenta consultar
    default boolean disponivel() {
        return true;
    }

    // servicoId é o id do serviço no Melhor Envio escolhido no checkout (pedido.servicoFreteId)
    record NovoEnvio(
            Long pedidoId,
            Integer servicoId,
            Destinatario destinatario,
            List<Item> itens,
            Volume volume,
            BigDecimal valorDeclarado
    ) {
    }

    // cep só com os 8 dígitos; uf é a sigla do estado
    record Destinatario(String nome, String telefone, String email, String cep, String logradouro, String numero,
                        String complemento, String bairro, String cidade, String uf) {
    }

    // Uma linha da declaração de conteúdo
    record Item(String nome, int quantidade, BigDecimal valorUnitario) {
    }

    // Um pacote só: Correios não aceita mais de um volume por envio
    record Volume(int alturaCm, int larguraCm, int comprimentoCm, BigDecimal pesoKg) {
    }

    // entregue: a transportadora informou a entrega (#64). entregueEm é o dia dela em São Paulo, ou null se o
    // Melhor Envio não informou.
    record SituacaoEnvio(Etapa etapa, String codigoRastreio, boolean entregue, LocalDate entregueEm) {

        // Ainda não entregue
        public SituacaoEnvio(Etapa etapa, String codigoRastreio) {
            this(etapa, codigoRastreio, false, null);
        }
    }

    enum Etapa {
        // No carrinho, ainda não pago
        NO_CARRINHO,
        // Pago, etiqueta ainda não gerada
        COMPRADO,
        // Etiqueta gerada (ou já postado, entregue...)
        GERADO,
        // Cancelado ou expirado no Melhor Envio: não serve mais
        CANCELADO
    }
}
