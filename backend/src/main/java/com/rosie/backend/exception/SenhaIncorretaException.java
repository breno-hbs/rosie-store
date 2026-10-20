package com.rosie.backend.exception;

// Senha atual errada numa ação que a exige como prova de que é a dona da conta (troca de e-mail, #61): 403
// SENHA_INCORRETA. Não é 401 porque a sessão continua valendo (um 401 faria a loja tirar a cliente da conta).
public class SenhaIncorretaException extends RuntimeException {

    public SenhaIncorretaException() {
        super("Senha atual incorreta");
    }
}
