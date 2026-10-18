package com.rosie.backend.service;

import com.rosie.backend.exception.AssinaturaInvalidaException;
import com.rosie.backend.exception.PagamentoFalhouException;
import com.rosie.backend.exception.PagamentoIndisponivelException;
import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.service.GatewayPagamento.Linha;
import com.rosie.backend.service.GatewayPagamento.NovaPreferencia;
import com.rosie.backend.service.GatewayPagamento.Pagamento;
import com.rosie.backend.service.GatewayPagamento.Preferencia;
import com.rosie.backend.service.GatewayPagamento.StatusPagamento;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Cliente HTTP do Mercado Pago contra um servidor simulado: nada aqui sai para a rede
class MercadoPagoGatewayTest {

    private static final String API = "https://api.mercadopago.com";

    private static final NovaPreferencia PEDIDO = new NovaPreferencia(42L, FormaPagamento.PIX, "Maria Silva",
            "maria@rosie.test", List.of(
            new Linha("produto-1-M", "Conjunto Serpente (tamanho M)", 2, new BigDecimal("189.00"), "https://img/1.jpg"),
            new Linha("frete", "Frete Correios PAC", 1, new BigDecimal("22.90"), null)));

    private MockRestServiceServer servidor;

    private MercadoPagoGateway gateway(String urlLoja) {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        return new MercadoPagoGateway(builder, API, "TEST-token", "segredo", "https://api.rosiestore.com.br/", urlLoja);
    }

    @Test
    void criaAPreferenciaDoCheckoutProNoFormatoDaApi() {
        MercadoPagoGateway gateway = gateway("https://rosiestore.com.br");
        servidor.expect(requestTo(API + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer TEST-token"))
                .andExpect(content().json("""
                        {
                          "items": [
                            { "id": "produto-1-M", "title": "Conjunto Serpente (tamanho M)", "quantity": 2,
                              "unit_price": 189.00, "currency_id": "BRL", "picture_url": "https://img/1.jpg" },
                            { "id": "frete", "title": "Frete Correios PAC", "quantity": 1, "unit_price": 22.90,
                              "currency_id": "BRL" }
                          ],
                          "payer": { "name": "Maria Silva", "email": "maria@rosie.test" },
                          "back_urls": {
                            "success": "https://rosiestore.com.br/pedido/42/sucesso",
                            "pending": "https://rosiestore.com.br/pedido/42/pendente",
                            "failure": "https://rosiestore.com.br/pedido/42/falha"
                          },
                          "auto_return": "approved",
                          "notification_url": "https://api.rosiestore.com.br/pagamentos/webhook",
                          "external_reference": "42",
                          "statement_descriptor": "ROSIE",
                          "payment_methods": {
                            "excluded_payment_types": [
                              { "id": "credit_card" }, { "id": "debit_card" }, { "id": "prepaid_card" },
                              { "id": "ticket" }, { "id": "atm" }
                            ]
                          }
                        }
                        """, true))
                .andRespond(withSuccess("""
                        { "id": "123-abc", "init_point": "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=123-abc",
                          "sandbox_init_point": "https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=123-abc",
                          "collector_id": 1 }
                        """, MediaType.APPLICATION_JSON));

        Preferencia preferencia = gateway.criarPreferencia(PEDIDO);

        assertThat(preferencia.id()).isEqualTo("123-abc");
        assertThat(preferencia.urlPagamento()).isEqualTo("https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=123-abc");
        servidor.verify();
    }

    @Test
    void cartaoTiraPixEBoletoELojaLocalNaoPedeRetornoAutomatico() {
        MercadoPagoGateway gateway = gateway("http://localhost:5173");
        servidor.expect(requestTo(API + "/checkout/preferences"))
                .andExpect(content().json("""
                        {
                          "back_urls": { "success": "http://localhost:5173/pedido/42/sucesso" },
                          "payment_methods": {
                            "excluded_payment_types": [
                              { "id": "debit_card" }, { "id": "prepaid_card" }, { "id": "ticket" },
                              { "id": "bank_transfer" }, { "id": "atm" }
                            ]
                          }
                        }
                        """))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request).getBodyAsString())
                        .doesNotContain("auto_return"))
                .andRespond(withSuccess("{ \"id\": \"1\", \"init_point\": \"https://mp/1\" }", MediaType.APPLICATION_JSON));

        gateway.criarPreferencia(new NovaPreferencia(42L, FormaPagamento.CARTAO, "Maria", "maria@rosie.test",
                PEDIDO.linhas()));
        servidor.verify();
    }

    @Test
    void tokenRecusadoViraIndisponivelEFalhaViraPagamentoFalhou() {
        MercadoPagoGateway gateway = gateway("https://rosiestore.com.br");
        servidor.expect(requestTo(API + "/checkout/preferences")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> gateway.criarPreferencia(PEDIDO)).isInstanceOf(PagamentoIndisponivelException.class);

        servidor.reset();
        servidor.expect(requestTo(API + "/checkout/preferences")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.criarPreferencia(PEDIDO)).isInstanceOf(PagamentoFalhouException.class);

        servidor.reset();
        servidor.expect(requestTo(API + "/checkout/preferences"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThatThrownBy(() -> gateway.criarPreferencia(PEDIDO)).isInstanceOf(PagamentoFalhouException.class);

        // Resposta sem link de pagamento
        servidor.reset();
        servidor.expect(requestTo(API + "/checkout/preferences"))
                .andRespond(withSuccess("{ \"id\": \"1\" }", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.criarPreferencia(PEDIDO)).isInstanceOf(PagamentoFalhouException.class);
    }

    @Test
    void consultaOPagamentoEConverteOStatus() {
        MercadoPagoGateway gateway = gateway("https://rosiestore.com.br");
        servidor.expect(requestTo(API + "/v1/payments/987654"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer TEST-token"))
                .andRespond(withSuccess("""
                        { "id": 987654, "status": "approved", "status_detail": "accredited",
                          "external_reference": "42", "transaction_amount": 400.90,
                          "payment_type_id": "bank_transfer", "payment_method_id": "pix" }
                        """, MediaType.APPLICATION_JSON));

        Pagamento pagamento = gateway.buscarPagamento("987654");

        assertThat(pagamento.id()).isEqualTo("987654");
        assertThat(pagamento.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.referenciaExterna()).isEqualTo("42");
        assertThat(pagamento.valor()).isEqualByComparingTo("400.90");

        servidor.reset();
        servidor.expect(requestTo(API + "/v1/payments/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> gateway.buscarPagamento("1")).isInstanceOf(PagamentoFalhouException.class);
    }

    @Test
    void statusDoMercadoPago() {
        assertThat(MercadoPagoGateway.status("approved")).isEqualTo(StatusPagamento.APROVADO);
        assertThat(MercadoPagoGateway.status("rejected")).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(MercadoPagoGateway.status("cancelled")).isEqualTo(StatusPagamento.CANCELADO);
        assertThat(MercadoPagoGateway.status("refunded")).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(MercadoPagoGateway.status("charged_back")).isEqualTo(StatusPagamento.ESTORNADO);
        for (String status : List.of("pending", "in_process", "authorized", "in_mediation", "novo_status")) {
            assertThat(MercadoPagoGateway.status(status)).isEqualTo(StatusPagamento.PENDENTE);
        }
    }

    @Test
    void validaAAssinaturaComOSegredoConfigurado() {
        MercadoPagoGateway gateway = gateway("https://rosiestore.com.br");
        String hash = AssinaturaWebhookMercadoPago.hmacHex("segredo", "id:987654;request-id:req-1;ts:1700000000;");

        gateway.validarAssinatura("ts=1700000000,v1=" + hash, "req-1", "987654");
        assertThatThrownBy(() -> gateway.validarAssinatura("ts=1700000000,v1=" + hash, "req-1", "987655"))
                .isInstanceOf(AssinaturaInvalidaException.class);
    }
}
