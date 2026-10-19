package com.rosie.backend.config;

import com.rosie.backend.service.EmailSoNoLog;
import com.rosie.backend.service.EnviadorEmail;
import com.rosie.backend.service.EnviadorEmail.Email;
import com.rosie.backend.service.ResendEnviador;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Escolha do envio de e-mail pelas variáveis (só monta o cliente: nada aqui chama o Resend)
class EmailConfigTest {

    private final EmailConfig config = new EmailConfig();

    @Test
    void comChaveERemetenteUsaOResend() {
        assertThat(montar("re_teste", "Rosie <contato@rosiestore.com.br>")).isInstanceOf(ResendEnviador.class);
    }

    @Test
    void semChaveOuRemetenteSoRegistraNoLog() {
        EnviadorEmail semChave = montar("", "Rosie <contato@rosiestore.com.br>");
        assertThat(semChave).isInstanceOf(EmailSoNoLog.class);
        assertThat(montar("re_teste", " ")).isInstanceOf(EmailSoNoLog.class);
        assertThat(montar("re_teste", "Rosie")).isInstanceOf(EmailSoNoLog.class);

        // O modo log não falha: a ação que disparou o e-mail segue normal
        assertThatCode(() -> semChave.enviar(new Email("maria@rosie.test", "Assunto", "<p>Oi</p>", "Oi", null)))
                .doesNotThrowAnyException();
    }

    private EnviadorEmail montar(String chave, String remetente) {
        return config.enviadorEmail(chave, "https://api.resend.com", remetente, "", Duration.ofSeconds(10));
    }
}
