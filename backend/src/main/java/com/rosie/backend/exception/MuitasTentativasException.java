package com.rosie.backend.exception;

// Pedidos de "esqueci minha senha" acima do limite por e-mail ou por IP (#93): HTTP 429 MUITAS_TENTATIVAS
public class MuitasTentativasException extends RuntimeException {

    public MuitasTentativasException() {
        super("Muitos pedidos seguidos. Espere um pouco e tente de novo.");
    }
}
