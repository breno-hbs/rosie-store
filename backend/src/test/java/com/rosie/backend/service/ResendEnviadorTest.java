package com.rosie.backend.service;

import com.rosie.backend.exception.EmailFalhouException;
import com.rosie.backend.service.EnviadorEmail.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Cliente HTTP do Resend contra um servidor simulado: nada aqui sai para a rede
class ResendEnviadorTest {

    private static final String BASE = "https://api.resend.com";
    private static final Email EMAIL = new Email("maria@rosie.test", "Troca do pedido #270 aprovada",
            "<p>Olá</p>", "Olá", "troca-33-APROVADA");

    private MockRestServiceServer servidor;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void mandaNoFormatoDaApiComChaveDeIdempotencia() {
        ResendEnviador enviador = new ResendEnviador(builder, BASE, "re_teste", "Rosie <contato@rosie.test>",
                "rosie@rosie.test");
        servidor.expect(requestTo(BASE + ResendEnviador.EMAILS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_teste"))
                .andExpect(header("Idempotency-Key", "troca-33-APROVADA"))
                .andExpect(content().json("""
                        {
                          "from": "Rosie <contato@rosie.test>",
                          "to": ["maria@rosie.test"],
                          "subject": "Troca do pedido #270 aprovada",
                          "html": "<p>Olá</p>",
                          "text": "Olá",
                          "reply_to": "rosie@rosie.test"
                        }
                        """, true))
                .andRespond(withSuccess("{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}", MediaType.APPLICATION_JSON));

        enviador.enviar(EMAIL);

        servidor.verify();
    }

    @Test
    void semResponderParaNemChaveNaoMandaOsCampos() {
        ResendEnviador enviador = new ResendEnviador(builder, BASE, "re_teste", "Rosie <contato@rosie.test>", null);
        servidor.expect(requestTo(BASE + ResendEnviador.EMAILS))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(content().string(not(containsString("reply_to"))))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        enviador.enviar(new Email("maria@rosie.test", "Assunto", "<p>Oi</p>", "Oi", null));

        servidor.verify();
    }

    @Test
    void recusaDoResendViraFalhaComOMotivo() {
        ResendEnviador enviador = new ResendEnviador(builder, BASE, "re_teste", "Rosie <contato@rosie.test>", null);
        servidor.expect(requestTo(BASE + ResendEnviador.EMAILS))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
                        {"statusCode":403,"name":"validation_error","message":"The rosie.test domain is not verified."}
                        """));

        assertThatThrownBy(() -> enviador.enviar(EMAIL))
                .isInstanceOf(EmailFalhouException.class)
                .hasMessage("O Resend recusou o e-mail (HTTP 403): validation_error, The rosie.test domain is not verified.");
    }

    @Test
    void semRespostaViraFalha() {
        ResendEnviador enviador = new ResendEnviador(builder, BASE, "re_teste", "Rosie <contato@rosie.test>", null);
        servidor.expect(requestTo(BASE + ResendEnviador.EMAILS))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> enviador.enviar(EMAIL))
                .isInstanceOf(EmailFalhouException.class)
                .hasMessageStartingWith("Não foi possível falar com o Resend");
    }

    @Test
    void destinatarioMascaradoNoLog() {
        assertThat(Email.mascarar("maria.cliente@gmail.com")).isEqualTo("ma***@gmail.com");
        assertThat(Email.mascarar("ab@rosie.test")).isEqualTo("a***@rosie.test");
        assertThat(Email.mascarar("@rosie.test")).isEqualTo("***");
    }
}
