package com.rosie.backend.config;

import com.rosie.backend.service.GatewayPagamento;
import com.rosie.backend.service.MercadoPagoGateway;
import com.rosie.backend.service.PagamentoIndisponivel;
import com.rosie.backend.service.PagamentoSimulado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// Pagamento pelo Mercado Pago (#24). Faltando o access token, o segredo do webhook ou a URL pública da API
// (para onde o Mercado Pago manda o webhook), a API sobe do mesmo jeito (dev local, CI) e o checkout
// responde 503 PAGAMENTO_INDISPONIVEL: sem webhook o pedido nunca seria confirmado, então não vale cobrar.
// PAGAMENTO_SIMULADO=true liga o pagamento de mentira para desenvolvimento.
@Configuration
@Slf4j
public class PagamentoConfig {

    @Bean
    public GatewayPagamento gatewayPagamento(
            @Value("${rosie.pagamento.simulado:false}") boolean simulado,
            @Value("${rosie.pagamento.mercado-pago.access-token:}") String accessToken,
            @Value("${rosie.pagamento.mercado-pago.segredo-webhook:}") String segredoWebhook,
            @Value("${rosie.pagamento.mercado-pago.url:https://api.mercadopago.com}") String url,
            @Value("${rosie.api.url-publica:}") String urlApi,
            @Value("${rosie.loja.url:http://localhost:5173}") String urlLoja,
            @Value("${rosie.pagamento.timeout:10s}") Duration timeout) {
        if (simulado) {
            log.warn("PAGAMENTO_SIMULADO ligado: o checkout usa um pagamento de mentira, sem o Mercado Pago.");
            return new PagamentoSimulado(urlLoja.trim());
        }
        if (vazio(accessToken)) {
            log.warn("MERCADO_PAGO_ACCESS_TOKEN não definido: o checkout vai responder 503.");
            return new PagamentoIndisponivel();
        }
        if (vazio(segredoWebhook)) {
            log.warn("MERCADO_PAGO_WEBHOOK_SECRET não definido: o checkout vai responder 503.");
            return new PagamentoIndisponivel();
        }
        if (vazio(urlApi) || !urlApi.trim().startsWith("https://")) {
            log.warn("ROSIE_API_URL ausente ou sem https: o Mercado Pago não teria para onde mandar o webhook, "
                    + "o checkout vai responder 503.");
            return new PagamentoIndisponivel();
        }

        // A cliente espera na tela do checkout enquanto a preferência é criada
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return new MercadoPagoGateway(RestClient.builder().requestFactory(fabrica), url.trim(), accessToken.trim(),
                segredoWebhook.trim(), urlApi.trim(), urlLoja.trim());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
