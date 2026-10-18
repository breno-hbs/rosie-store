package com.rosie.backend.exception;

import lombok.Getter;

// Regra de negócio que impede a operação (HTTP 409). O código vai para o campo "codigo" do erro.
@Getter
public class ConflitoException extends RuntimeException {

    private final String codigo;

    public ConflitoException(String mensagem, String codigo) {
        super(mensagem);
        this.codigo = codigo;
    }
}
