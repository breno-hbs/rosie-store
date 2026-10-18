package com.rosie.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rosie.backend.exception.AssinaturaInvalidaException;
import com.rosie.backend.exception.PagamentoFalhouException;
import com.rosie.backend.exception.PagamentoIndisponivelException;
import com.rosie.backend.model.FormaPagamento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;

// Mercado Pago pela API REST (Checkout Pro). O RestClient.Builder chega com os timeouts (PagamentoConfig).
// - Preferência: POST /checkout/preferences; a cliente paga no init_point.
//   https://www.mercadopago.com.br/developers/pt/reference/preferences/_checkout_preferences/post
// - Pagamento: GET /v1/payments/{id}, chamado a cada webhook.
//   https://www.mercadopago.com.br/developers/pt/reference/payments/_payments_id/get
// - Webhook: assinatura x-signature (AssinaturaWebhookMercadoPago).
@Slf4j
public class MercadoPagoGateway implements GatewayPagamento {

    static final String CAMINHO_PREFERENCIAS = "/checkout/preferences";
    static final String CAMINHO_PAGAMENTO = "/v1/payments/{id}";
    static final String CAMINHO_WEBHOOK = "/pagamentos/webhook";

    // Tipos de pagamento do Mercado Pago que cada forma da tela NÃO mostra. Cartão = crédito (com parcelas);
    // PIX é o "bank_transfer" no Brasil. Boleto (ticket) e lotérica (atm) ficam sempre de fora.
    static final List<String> EXCLUIDOS_CARTAO = List.of("debit_card", "prepaid_card", "ticket", "bank_transfer", "atm");
    static final List<String> EXCLUIDOS_PIX = List.of("credit_card", "debit_card", "prepaid_card", "ticket", "atm");

    private static final String FALHOU = "Não foi possível iniciar o pagamento agora. Tente de novo em instantes.";

    private final RestClient restClient;
    private final String segredoWebhook;
    private final String urlApi;
    private final String urlLoja;

    public MercadoPagoGateway(RestClient.Builder builder, String urlBase, String accessToken, String segredoWebhook,
                              String urlApi, String urlLoja) {
        this.restClient = builder
                .baseUrl(urlBase)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.segredoWebhook = segredoWebhook;
        this.urlApi = semBarraNoFim(urlApi);
        this.urlLoja = semBarraNoFim(urlLoja);
    }

    @Override
    public Preferencia criarPreferencia(NovaPreferencia dados) {
        String retorno = urlLoja + "/pedido/" + dados.pedidoId();
        List<String> excluidos = dados.formaPagamento() == FormaPagamento.PIX ? EXCLUIDOS_PIX : EXCLUIDOS_CARTAO;

        PreferenciaRequest corpo = new PreferenciaRequest(
                dados.linhas().stream().map(Item::from).toList(),
                new Pagador(dados.nomeCliente(), dados.emailCliente()),
                new UrlsRetorno(retorno + "/sucesso", retorno + "/pendente", retorno + "/falha"),
                // O Mercado Pago só volta sozinho para a loja com URL pública (não aceita localhost)
                urlLoja.startsWith("https://") ? "approved" : null,
                urlApi + CAMINHO_WEBHOOK,
                String.valueOf(dados.pedidoId()),
                "ROSIE",
                new MeiosPagamento(excluidos.stream().map(Tipo::new).toList()));

        PreferenciaResponse resposta;
        try {
            resposta = restClient.post()
                    .uri(CAMINHO_PREFERENCIAS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(PreferenciaResponse.class);
        } catch (RestClientResponseException e) {
            throw traduzirErro(e);
        } catch (RestClientException e) {
            log.warn("Mercado Pago sem resposta válida ao criar a preferência: {}", e.getMessage());
            throw new PagamentoFalhouException(FALHOU, e);
        }

        String url = resposta == null ? null
                : resposta.initPoint() != null ? resposta.initPoint() : resposta.sandboxInitPoint();
        if (resposta == null || resposta.id() == null || url == null) {
            throw new PagamentoFalhouException(FALHOU);
        }
        return new Preferencia(resposta.id(), url);
    }

    @Override
    public Pagamento buscarPagamento(String pagamentoId) {
        PagamentoResponse resposta;
        try {
            resposta = restClient.get()
                    .uri(CAMINHO_PAGAMENTO, pagamentoId)
                    .retrieve()
                    .body(PagamentoResponse.class);
        } catch (RestClientResponseException e) {
            throw traduzirErro(e);
        } catch (RestClientException e) {
            log.warn("Mercado Pago sem resposta válida ao consultar o pagamento {}: {}", pagamentoId, e.getMessage());
            throw new PagamentoFalhouException("Não foi possível consultar o pagamento", e);
        }
        if (resposta == null || resposta.id() == null || resposta.status() == null) {
            throw new PagamentoFalhouException("Resposta inesperada ao consultar o pagamento " + pagamentoId);
        }
        return new Pagamento(String.valueOf(resposta.id()), status(resposta.status()), resposta.externalReference(),
                resposta.transactionAmount());
    }

    @Override
    public void validarAssinatura(String xSignature, String xRequestId, String dataId) {
        if (!AssinaturaWebhookMercadoPago.valida(xSignature, xRequestId, dataId, segredoWebhook)) {
            throw new AssinaturaInvalidaException("Assinatura do webhook não confere");
        }
    }

    // https://www.mercadopago.com.br/developers/pt/docs/checkout-api/response-handling/collection-results
    static StatusPagamento status(String status) {
        return switch (status) {
            case "approved" -> StatusPagamento.APROVADO;
            case "rejected" -> StatusPagamento.RECUSADO;
            case "cancelled" -> StatusPagamento.CANCELADO;
            case "refunded", "charged_back" -> StatusPagamento.ESTORNADO;
            // pending, in_process, authorized, in_mediation e qualquer status novo: ainda não decide nada
            default -> StatusPagamento.PENDENTE;
        };
    }

    private RuntimeException traduzirErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            log.error("Mercado Pago recusou o access token ({}): confira MERCADO_PAGO_ACCESS_TOKEN", status);
            return new PagamentoIndisponivelException("O pagamento está indisponível no momento.");
        }
        log.warn("Mercado Pago respondeu {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return new PagamentoFalhouException(FALHOU, e);
    }

    private static String semBarraNoFim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ----- Formato da API (snake_case) -----

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PreferenciaRequest(
            List<Item> items,
            Pagador payer,
            @JsonProperty("back_urls") UrlsRetorno backUrls,
            @JsonProperty("auto_return") String autoReturn,
            @JsonProperty("notification_url") String notificationUrl,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("statement_descriptor") String statementDescriptor,
            @JsonProperty("payment_methods") MeiosPagamento paymentMethods
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Item(String id, String title, int quantity, @JsonProperty("unit_price") BigDecimal unitPrice,
                @JsonProperty("currency_id") String currencyId, @JsonProperty("picture_url") String pictureUrl) {

        static Item from(Linha linha) {
            return new Item(linha.id(), linha.titulo(), linha.quantidade(), linha.precoUnitario(), "BRL",
                    linha.imagemUrl());
        }
    }

    record Pagador(String name, String email) {
    }

    record UrlsRetorno(String success, String pending, String failure) {
    }

    record MeiosPagamento(@JsonProperty("excluded_payment_types") List<Tipo> excludedPaymentTypes) {
    }

    record Tipo(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreferenciaResponse(
            String id,
            @JsonProperty("init_point") String initPoint,
            @JsonProperty("sandbox_init_point") String sandboxInitPoint
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PagamentoResponse(
            Long id,
            String status,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("transaction_amount") BigDecimal transactionAmount
    ) {
    }
}
