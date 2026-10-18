package com.rosie.backend.exception;

// O Mercado Pago não respondeu a tempo, caiu ou devolveu algo inesperado: HTTP 502 PAGAMENTO_FALHOU
public class PagamentoFalhouException extends RuntimeException {

    public PagamentoFalhouException(String mensagem) {
        super(mensagem);
    }

    public PagamentoFalhouException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
