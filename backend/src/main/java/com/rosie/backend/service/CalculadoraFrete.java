package com.rosie.backend.service;

import java.math.BigDecimal;
import java.util.List;

// Cotação de frete (RF11, #23). Em produção é o Melhor Envio; nos testes, uma implementação falsa,
// para o CI não depender do token nem da rede.
public interface CalculadoraFrete {

    // Opções de entrega das peças até o CEP (8 dígitos, sem hífen). Serviços que a transportadora
    // recusa para o trecho não entram. Lança FreteIndisponivelException (sem configuração) ou
    // FreteFalhouException (serviço fora do ar, timeout, resposta inesperada).
    List<OpcaoFrete> calcular(String cepDestino, List<PecaFrete> pecas);

    // Uma linha do carrinho: quantidade de peças iguais, com o valor declarado (seguro) e o tamanho
    // de cada peça já embalada. O Melhor Envio junta as peças nos pacotes.
    record PecaFrete(
            String id,
            int quantidade,
            BigDecimal valorUnitario,
            BigDecimal pesoKg,
            int larguraCm,
            int alturaCm,
            int comprimentoCm
    ) {
    }

    // servicoId é o id do serviço no Melhor Envio (ex.: 1 = PAC, 2 = SEDEX): o checkout (#24) usa ele
    // para cotar de novo e conferir o preço
    record OpcaoFrete(Integer servicoId, String transportadora, String servico, BigDecimal preco, int prazoDias) {
    }
}
