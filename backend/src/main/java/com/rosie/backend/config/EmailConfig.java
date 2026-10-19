package com.rosie.backend.config;

import com.rosie.backend.service.EmailSoNoLog;
import com.rosie.backend.service.EnviadorEmail;
import com.rosie.backend.service.ResendEnviador;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// E-mail transacional pelo Resend (#65). Sem RESEND_API_KEY (dev local, CI), a API sobe do mesmo jeito e os
// e-mails só vão para o log (destinatário mascarado e assunto). O remetente (ROSIE_EMAIL_REMETENTE) precisa
// estar num domínio verificado no Resend.
@Configuration
@Slf4j
public class EmailConfig {

    @Bean
    public EnviadorEmail enviadorEmail(
            @Value("${rosie.email.resend.api-key:}") String apiKey,
            @Value("${rosie.email.resend.url:https://api.resend.com}") String url,
            @Value("${rosie.email.remetente:}") String remetente,
            @Value("${rosie.email.responder-para:}") String responderPara,
            @Value("${rosie.email.timeout:10s}") Duration timeout) {
        if (vazio(apiKey)) {
            log.warn("RESEND_API_KEY não definida: os e-mails não saem, só ficam registrados no log.");
            return new EmailSoNoLog();
        }
        if (vazio(remetente) || !remetente.contains("@")) {
            log.warn("ROSIE_EMAIL_REMETENTE ausente ou sem e-mail: os e-mails não saem, só ficam registrados no log.");
            return new EmailSoNoLog();
        }

        // O envio roda fora da requisição (FilaEmail): o tempo limite só segura a fila
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return new ResendEnviador(RestClient.builder().requestFactory(fabrica), url.trim(), apiKey.trim(),
                remetente.trim(), vazio(responderPara) ? null : responderPara.trim());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
