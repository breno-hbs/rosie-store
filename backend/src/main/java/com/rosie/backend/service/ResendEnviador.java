package com.rosie.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rosie.backend.exception.EmailFalhouException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

// E-mail pela API do Resend (#65): POST /emails, https://resend.com/docs/api-reference/emails/send-email
// O remetente precisa estar num domínio verificado no Resend (registros SPF e DKIM no DNS de
// rosiestore.com.br); sem isso o Resend responde 403 e o erro vai para o log.
@Slf4j
public class ResendEnviador implements EnviadorEmail {

    static final String EMAILS = "/emails";

    private final RestClient restClient;
    private final String remetente;
    private final String responderPara;

    // remetente no formato "Rosie <contato@rosiestore.com.br>"; responderPara (opcional) recebe as respostas
    // da cliente
    public ResendEnviador(RestClient.Builder builder, String urlBase, String apiKey, String remetente,
                          String responderPara) {
        this.restClient = builder
                .baseUrl(urlBase)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.USER_AGENT, "Rosie (rosiestore.com.br)")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.remetente = remetente;
        this.responderPara = responderPara;
    }

    @Override
    public void enviar(Email email) {
        Envio envio = new Envio(remetente, List.of(email.para()), email.assunto(), email.html(), email.texto(),
                responderPara);
        Enviado enviado;
        try {
            enviado = restClient.post().uri(EMAILS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(cabecalhos -> {
                        if (email.chave() != null) cabecalhos.set("Idempotency-Key", email.chave());
                    })
                    .body(envio)
                    .retrieve()
                    .body(Enviado.class);
        } catch (RestClientResponseException e) {
            throw new EmailFalhouException("O Resend recusou o e-mail (HTTP " + e.getStatusCode().value() + "): "
                    + erro(e), e);
        } catch (RestClientException e) {
            throw new EmailFalhouException("Não foi possível falar com o Resend: " + e.getMessage(), e);
        }
        log.info("E-mail enviado pelo Resend: para {}, assunto \"{}\", id {}", email.paraMascarado(),
                email.assunto(), enviado == null ? null : enviado.id());
    }

    // O Resend responde { "statusCode": 403, "name": "validation_error", "message": "..." }; o corpo cru
    // fica de fora do log, que não precisa do resto
    private static String erro(RestClientResponseException e) {
        try {
            Erro erro = e.getResponseBodyAs(Erro.class);
            if (erro != null && erro.message() != null) {
                return erro.name() == null ? erro.message() : erro.name() + ", " + erro.message();
            }
        } catch (RuntimeException ignorada) {
            // Corpo fora do formato esperado (ex.: página de erro de um proxy)
        }
        return e.getStatusText();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Envio(String from, List<String> to, String subject, String html, String text,
                 @JsonProperty("reply_to") String replyTo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Enviado(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Erro(String name, String message) {
    }
}
