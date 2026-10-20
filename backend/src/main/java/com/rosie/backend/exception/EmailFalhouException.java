package com.rosie.backend.exception;

// O provedor de e-mail recusou a mensagem, não respondeu a tempo ou devolveu algo inesperado. Nos avisos (pela
// FilaEmail) não vira resposta HTTP: vai para o log e a ação que disparou o e-mail segue normal. Só o link de
// confirmação da troca de e-mail (#61), enviado na hora, responde 502 EMAIL_FALHOU.
public class EmailFalhouException extends RuntimeException {

    public EmailFalhouException(String mensagem) {
        super(mensagem);
    }

    public EmailFalhouException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
