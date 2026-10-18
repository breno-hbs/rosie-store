package com.rosie.backend.config;

import com.rosie.backend.service.CalculadoraFrete;
import com.rosie.backend.service.FreteIndisponivel;
import com.rosie.backend.service.FreteSimulado;
import com.rosie.backend.service.MelhorEnvioCalculadora;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// Cálculo de frete (#23). Sem MELHOR_ENVIO_TOKEN ou sem ROSIE_CEP_ORIGEM a API sobe do mesmo jeito
// (dev local, CI) e só o cálculo responde 503 FRETE_INDISPONIVEL. FRETE_SIMULADO=true liga opções fixas
// para desenvolvimento.
@Configuration
@Slf4j
public class FreteConfig {

    @Bean
    public CalculadoraFrete calculadoraFrete(
            @Value("${rosie.frete.simulado:false}") boolean simulado,
            @Value("${rosie.frete.melhor-envio.token:}") String token,
            @Value("${rosie.frete.melhor-envio.url:https://sandbox.melhorenvio.com.br}") String url,
            @Value("${rosie.frete.melhor-envio.user-agent:Rosie (rosiestore.com.br)}") String userAgent,
            @Value("${rosie.frete.cep-origem:}") String cepOrigem,
            @Value("${rosie.frete.timeout:5s}") Duration timeout) {
        if (simulado) {
            log.warn("FRETE_SIMULADO ligado: o frete usa opções fixas de desenvolvimento, sem o Melhor Envio.");
            return new FreteSimulado();
        }
        if (token == null || token.isBlank()) {
            log.warn("MELHOR_ENVIO_TOKEN não definido: o cálculo de frete vai responder 503.");
            return new FreteIndisponivel();
        }
        String cep = cepOrigem == null ? "" : cepOrigem.replaceAll("\\D", "");
        if (cep.length() != 8) {
            log.warn("ROSIE_CEP_ORIGEM ausente ou sem 8 dígitos: o cálculo de frete vai responder 503.");
            return new FreteIndisponivel();
        }

        // Timeout curto: a cliente está esperando na tela do carrinho
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return new MelhorEnvioCalculadora(RestClient.builder().requestFactory(fabrica),
                url.trim(), token.trim(), userAgent.trim(), cep);
    }
}
