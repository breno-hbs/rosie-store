package com.rosie.backend.exception;

// O Melhor Envio não respondeu a tempo, caiu ou devolveu algo inesperado: HTTP 502 FRETE_FALHOU
public class FreteFalhouException extends RuntimeException {

    public FreteFalhouException(String mensagem) {
        super(mensagem);
    }

    public FreteFalhouException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
