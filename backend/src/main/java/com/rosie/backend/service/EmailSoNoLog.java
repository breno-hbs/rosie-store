package com.rosie.backend.service;

import lombok.extern.slf4j.Slf4j;

// Modo de desenvolvimento, usado quando falta RESEND_API_KEY (dev local, CI): o e-mail não sai, só fica no
// log o destinatário (mascarado) e o assunto. O corpo pode ter o recado, dados da cliente e o link de troca de
// e-mail (#61), então só vai para o log em DEBUG, que fica desligado por padrão: no dev, ligue com
// logging.level.com.rosie.backend.service.EmailSoNoLog=DEBUG para copiar o link de confirmação do console.
@Slf4j
public class EmailSoNoLog implements EnviadorEmail {

    @Override
    public void enviar(Email email) {
        log.info("E-mail não enviado (RESEND_API_KEY não definida): para {}, assunto \"{}\"",
                email.paraMascarado(), email.assunto());
        if (log.isDebugEnabled()) {
            log.debug("Texto do e-mail para {}:\n{}", email.paraMascarado(), email.texto());
        }
    }
}
