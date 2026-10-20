package com.rosie.backend.exception;

import lombok.Getter;

// Link de confirmação que não serve (#61), HTTP 400: TOKEN_INVALIDO (não existe, já foi usado ou foi trocado
// por um pedido mais novo) ou TOKEN_EXPIRADO (passou do prazo; a cliente pede a troca de novo)
@Getter
public class TokenInvalidoException extends RuntimeException {

    private final String codigo;

    private TokenInvalidoException(String mensagem, String codigo) {
        super(mensagem);
        this.codigo = codigo;
    }

    public static TokenInvalidoException invalido() {
        return new TokenInvalidoException("Este link não é válido ou já foi usado", "TOKEN_INVALIDO");
    }

    public static TokenInvalidoException expirado() {
        return new TokenInvalidoException("Este link expirou. Peça a troca de e-mail de novo", "TOKEN_EXPIRADO");
    }
}
