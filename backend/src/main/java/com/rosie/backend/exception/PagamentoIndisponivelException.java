package com.rosie.backend.exception;

// Pagamento não configurado (sem as variáveis do Mercado Pago) ou access token recusado pelo Mercado Pago:
// HTTP 503 PAGAMENTO_INDISPONIVEL
public class PagamentoIndisponivelException extends RuntimeException {

    public PagamentoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
