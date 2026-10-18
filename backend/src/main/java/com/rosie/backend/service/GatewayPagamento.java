package com.rosie.backend.service;

import com.rosie.backend.model.FormaPagamento;

import java.math.BigDecimal;
import java.util.List;

// Pagamento do pedido (RF10, #24). Em produção é o Mercado Pago pelo Checkout Pro: a cliente paga na
// página do Mercado Pago, e os dados do cartão nunca passam pela API. Nos testes, uma implementação
// falsa, para o CI não depender de credencial nem de rede.
public interface GatewayPagamento {

    // Cria o link de pagamento do pedido. Lança PagamentoIndisponivelException (sem configuração ou
    // credencial recusada) ou PagamentoFalhouException (fora do ar, timeout, resposta inesperada).
    Preferencia criarPreferencia(NovaPreferencia dados);

    // Consulta o pagamento no gateway: o webhook só avisa que algo mudou, o status vem daqui.
    // Mesmas exceções de criarPreferencia; pagamento que não existe é PagamentoFalhouException.
    Pagamento buscarPagamento(String pagamentoId);

    // Confere a assinatura do webhook. Lança AssinaturaInvalidaException se não bater (ou faltar).
    void validarAssinatura(String xSignature, String xRequestId, String dataId);

    // linhas: as peças e, por último, o frete. referencia volta no pagamento (external_reference).
    record NovaPreferencia(
            Long pedidoId,
            FormaPagamento formaPagamento,
            String nomeCliente,
            String emailCliente,
            List<Linha> linhas
    ) {
    }

    record Linha(String id, String titulo, int quantidade, BigDecimal precoUnitario, String imagemUrl) {
    }

    record Preferencia(String id, String urlPagamento) {
    }

    // referenciaExterna é o id do pedido (texto); valor é o valor da compra, sem juros do parcelamento
    record Pagamento(String id, StatusPagamento status, String referenciaExterna, BigDecimal valor) {
    }

    enum StatusPagamento {
        APROVADO,
        // Em análise, PIX esperando o pagamento, autorizado sem captura
        PENDENTE,
        RECUSADO,
        // Cancelado ou expirado
        CANCELADO,
        // Devolvido à cliente ou contestado no cartão
        ESTORNADO
    }
}
