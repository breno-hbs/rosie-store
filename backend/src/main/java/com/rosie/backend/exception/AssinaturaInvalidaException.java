package com.rosie.backend.exception;

// Webhook sem assinatura, com assinatura malformada ou que não bate com o segredo: HTTP 401 ASSINATURA_INVALIDA
public class AssinaturaInvalidaException extends RuntimeException {

    public AssinaturaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
