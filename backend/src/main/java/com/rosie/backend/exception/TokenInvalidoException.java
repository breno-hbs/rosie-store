package com.rosie.backend.exception;

import lombok.Getter;

// Link mandado por e-mail que não serve (confirmação da troca de e-mail, #61, e redefinição de senha, #93),
// HTTP 400: TOKEN_INVALIDO (não existe, já foi usado ou foi trocado por um pedido mais novo) ou TOKEN_EXPIRADO
// (passou do prazo; a pessoa pede um link novo)
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
        return expirado("Este link expirou. Peça a troca de e-mail de novo");
    }

    public static TokenInvalidoException expirado(String mensagem) {
        return new TokenInvalidoException(mensagem, "TOKEN_EXPIRADO");
    }
}
