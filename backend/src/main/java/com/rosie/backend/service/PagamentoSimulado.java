package com.rosie.backend.service;

import com.rosie.backend.exception.AssinaturaInvalidaException;
import com.rosie.backend.exception.PagamentoFalhouException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Modo de desenvolvimento (PAGAMENTO_SIMULADO=true): sem chamar o Mercado Pago, para testar o checkout
// sem credencial. Nunca ligar em produção. O "link de pagamento" leva à página /pedido/{id}/pagamento-simulado
// da loja, onde a cliente escolhe aprovar, recusar ou cancelar; a loja chama
// POST /pedidos/{id}/pagamento-simulado, que registra o pagamento aqui e o processa pelo mesmo caminho do
// webhook (PagamentoService.processar). Os pagamentos ficam só na memória.
public class PagamentoSimulado implements GatewayPagamento {

    private final String urlLoja;
    private final Map<String, Pagamento> pagamentos = new ConcurrentHashMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public PagamentoSimulado(String urlLoja) {
        this.urlLoja = urlLoja;
    }

    @Override
    public Preferencia criarPreferencia(NovaPreferencia dados) {
        return new Preferencia("simulada-" + dados.pedidoId(),
                urlLoja + "/pedido/" + dados.pedidoId() + "/pagamento-simulado");
    }

    @Override
    public Pagamento buscarPagamento(String pagamentoId) {
        Pagamento pagamento = pagamentos.get(pagamentoId);
        if (pagamento == null) throw new PagamentoFalhouException("Pagamento simulado " + pagamentoId + " não existe");
        return pagamento;
    }

    // No modo simulado não há webhook do Mercado Pago
    @Override
    public void validarAssinatura(String xSignature, String xRequestId, String dataId) {
        throw new AssinaturaInvalidaException("Webhook desligado no modo simulado");
    }

    // Cria um pagamento simulado do pedido e devolve o id dele, para processar como se viesse do webhook
    public String registrar(Long pedidoId, StatusPagamento status, BigDecimal valor) {
        String id = "simulado-" + pedidoId + "-" + sequencia.incrementAndGet();
        pagamentos.put(id, new Pagamento(id, status, String.valueOf(pedidoId), valor));
        return id;
    }
}
