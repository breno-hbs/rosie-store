package com.rosie.backend.service;

import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.FreteFalhouException;
import com.rosie.backend.exception.FreteIndisponivelException;
import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;
import com.rosie.backend.service.CalculadoraFrete.PecaFrete;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

// Cliente HTTP do Melhor Envio contra um servidor simulado: nada aqui sai para a rede
class MelhorEnvioCalculadoraTest {

    private static final String URL = "https://sandbox.melhorenvio.com.br/api/v2/me/shipment/calculate";

    private static final List<PecaFrete> PECAS = List.of(
            new PecaFrete("produto-10-P", 2, new BigDecimal("159.90"), new BigDecimal("0.3"), 20, 5, 30));

    private MockRestServiceServer servidor;
    private MelhorEnvioCalculadora calculadora;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        calculadora = new MelhorEnvioCalculadora(builder, "https://sandbox.melhorenvio.com.br", "token-teste",
                "Rosie (teste@rosie.test)", "01310100");
    }

    @Test
    void mandaACotacaoNoFormatoDaApiEIgnoraServicosComErro() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-teste"))
                .andExpect(header(HttpHeaders.USER_AGENT, "Rosie (teste@rosie.test)"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {
                          "from": { "postal_code": "01310100" },
                          "to": { "postal_code": "03925050" },
                          "products": [
                            { "id": "produto-10-P", "width": 20, "height": 5, "length": 30, "weight": 0.3,
                              "insurance_value": 159.90, "quantity": 2 }
                          ],
                          "options": { "receipt": false, "own_hand": false }
                        }
                        """, true))
                .andRespond(withSuccess("""
                        [
                          { "id": 1, "name": "PAC", "price": "24.10", "custom_price": "22.90", "currency": "R$",
                            "delivery_time": 8, "custom_delivery_time": 7,
                            "company": { "id": 1, "name": "Correios", "picture": "https://..." } },
                          { "id": 2, "name": "SEDEX", "price": "38.50", "delivery_time": 3,
                            "company": { "id": 1, "name": "Correios" } },
                          { "id": 3, "name": ".Package", "error": "Serviço indisponível para o trecho.",
                            "company": { "id": 2, "name": "Jadlog" } }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<OpcaoFrete> opcoes = calculadora.calcular("03925050", PECAS);

        servidor.verify();
        assertThat(opcoes).hasSize(2);
        // custom_price / custom_delivery_time têm prioridade (já trazem as taxas e descontos da conta)
        assertThat(opcoes.get(0)).isEqualTo(new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7));
        assertThat(opcoes.get(1)).isEqualTo(new OpcaoFrete(2, "Correios", "SEDEX", new BigDecimal("38.50"), 3));
    }

    @Test
    void tokenRecusadoFicaIndisponivel() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Unauthenticated.\"}"));

        assertThatThrownBy(() -> calculadora.calcular("03925050", PECAS))
                .isInstanceOf(FreteIndisponivelException.class);
    }

    @Test
    void cepDeDestinoRecusadoViraDadosInvalidos() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        { "message": "The given data was invalid.",
                          "errors": { "to.postal_code": ["O campo to.postal_code é inválido."] } }
                        """));

        assertThatThrownBy(() -> calculadora.calcular("00000000", PECAS))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessageContaining("CEP não encontrado");
    }

    @Test
    void outro422EErroDoServidorViramFalha() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ \"message\": \"invalid\", \"errors\": { \"from.postal_code\": [\"inválido\"] } }"));
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> calculadora.calcular("03925050", PECAS)).isInstanceOf(FreteFalhouException.class);
        assertThatThrownBy(() -> calculadora.calcular("03925050", PECAS)).isInstanceOf(FreteFalhouException.class);
    }

    @Test
    void timeoutViraFalha() {
        servidor.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> calculadora.calcular("03925050", PECAS))
                .isInstanceOf(FreteFalhouException.class)
                .hasMessageContaining("Tente de novo");
    }

    @Test
    void respostaQueNaoEListaViraFalha() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"message\":\"???\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> calculadora.calcular("03925050", PECAS)).isInstanceOf(FreteFalhouException.class);
    }
}
