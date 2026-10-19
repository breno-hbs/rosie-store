package com.rosie.backend.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

// Cliente HTTP da API do Melhor Envio, o mesmo para a cotação (#23) e para a etiqueta (#25): URL base
// (sandbox ou produção), token e os cabeçalhos que a API exige. O builder chega com os timeouts já
// definidos (FreteConfig / EtiquetaConfig).
final class MelhorEnvioApi {

    private MelhorEnvioApi() {
    }

    static RestClient cliente(RestClient.Builder builder, String urlBase, String token, String userAgent) {
        return builder
                .baseUrl(urlBase)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // O Melhor Envio exige User-Agent com o nome da aplicação e um e-mail de contato
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }
}
