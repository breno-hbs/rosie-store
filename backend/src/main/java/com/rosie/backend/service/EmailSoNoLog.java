package com.rosie.backend.service;

import lombok.extern.slf4j.Slf4j;

// Modo de desenvolvimento, usado quando falta RESEND_API_KEY (dev local, CI): o e-mail não sai, só fica no
// log o destinatário (mascarado) e o assunto. O corpo não vai para o log: pode ter o recado e dados da cliente.
@Slf4j
public class EmailSoNoLog implements EnviadorEmail {

    @Override
    public void enviar(Email email) {
        log.info("E-mail não enviado (RESEND_API_KEY não definida): para {}, assunto \"{}\"",
                email.paraMascarado(), email.assunto());
    }
}
