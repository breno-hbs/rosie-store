package com.rosie.backend.exception;

// O provedor de e-mail recusou a mensagem, não respondeu a tempo ou devolveu algo inesperado. Não vira
// resposta HTTP: a FilaEmail registra no log e a ação que disparou o e-mail segue normal.
public class EmailFalhouException extends RuntimeException {

    public EmailFalhouException(String mensagem) {
        super(mensagem);
    }

    public EmailFalhouException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
