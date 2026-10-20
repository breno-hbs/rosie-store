package com.rosie.backend.service;

import com.rosie.backend.exception.EtiquetaFalhouException;
import com.rosie.backend.exception.EtiquetaIndisponivelException;
import com.rosie.backend.service.EmissorEtiqueta.Destinatario;
import com.rosie.backend.service.EmissorEtiqueta.Etapa;
import com.rosie.backend.service.EmissorEtiqueta.Item;
import com.rosie.backend.service.EmissorEtiqueta.NovoEnvio;
import com.rosie.backend.service.EmissorEtiqueta.SituacaoEnvio;
import com.rosie.backend.service.EmissorEtiqueta.Volume;
import com.rosie.backend.service.MelhorEnvioEmissor.Remetente;
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
import java.time.LocalDate;
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

// Cliente HTTP da etiqueta contra um servidor simulado: nada aqui sai para a rede nem gasta saldo
class MelhorEnvioEmissorTest {

    private static final String BASE = "https://sandbox.melhorenvio.com.br";
    private static final String ID = "9b0a3c1e-1d2f-4a5b-8c7d-6e5f4a3b2c1d";

    private static final Remetente LOJA = new Remetente("Rosie Store", "11999990000", "contato@rosie.test",
            "12345678909", null, "01310100", "Avenida Paulista", "1000", "Sala 5", "Bela Vista", "São Paulo", "SP");

    private MockRestServiceServer servidor;
    private RestClient.Builder builder;
    private MelhorEnvioEmissor emissor;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        emissor = new MelhorEnvioEmissor(builder, BASE, "token-teste", "Rosie (teste@rosie.test)", LOJA);
    }

    @Test
    void poeNoCarrinhoNoFormatoDaApiComDeclaracaoDeConteudo() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.CARRINHO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-teste"))
                .andExpect(header(HttpHeaders.USER_AGENT, "Rosie (teste@rosie.test)"))
                .andExpect(content().json("""
                        {
                          "service": 1,
                          "from": {
                            "name": "Rosie Store", "phone": "11999990000", "email": "contato@rosie.test",
                            "document": "12345678909", "address": "Avenida Paulista", "complement": "Sala 5",
                            "number": "1000", "district": "Bela Vista", "city": "São Paulo", "country_id": "BR",
                            "postal_code": "01310100", "state_abbr": "SP"
                          },
                          "to": {
                            "name": "Maria Cliente", "phone": "11988887777", "email": "maria@rosie.test",
                            "address": "Rua das Flores", "number": "120", "district": "Jardim Iva",
                            "city": "São Paulo", "country_id": "BR", "postal_code": "03925050", "state_abbr": "SP"
                          },
                          "products": [ { "name": "Vestido Aurora (M)", "quantity": "2", "unitary_value": "159.90" } ],
                          "volumes": [ { "height": 10, "width": 20, "length": 30, "weight": 0.6 } ],
                          "options": {
                            "insurance_value": 319.80, "receipt": false, "own_hand": false, "reverse": false,
                            "non_commercial": true, "tags": [ { "tag": "Pedido 42" } ]
                          }
                        }
                        """, true))
                .andRespond(withSuccess("""
                        { "id": "%s", "protocol": "ORD-202610001", "service_id": 1, "status": "pending", "price": 22.9 }
                        """.formatted(ID), MediaType.APPLICATION_JSON));

        String id = emissor.adicionarAoCarrinho(new NovoEnvio(42L, 1,
                new Destinatario("Maria Cliente", "11988887777", "maria@rosie.test", "03925050", "Rua das Flores",
                        "120", null, "Jardim Iva", "São Paulo", "SP"),
                List.of(new Item("Vestido Aurora (M)", 2, new BigDecimal("159.90"))),
                new Volume(10, 20, 30, new BigDecimal("0.6")),
                new BigDecimal("319.80")));

        assertThat(id).isEqualTo(ID);
        servidor.verify();
    }

    @Test
    void voltaDaTrocaEReversaComAClienteNoFromEALojaNoTo() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.CARRINHO))
                .andExpect(content().json("""
                        {
                          "service": 1,
                          "from": {
                            "name": "Maria Cliente", "phone": "11988887777", "email": "maria@rosie.test",
                            "address": "Rua das Flores", "number": "120", "district": "Jardim Iva",
                            "city": "São Paulo", "country_id": "BR", "postal_code": "03925050", "state_abbr": "SP"
                          },
                          "to": {
                            "name": "Rosie Store", "phone": "11999990000", "email": "contato@rosie.test",
                            "document": "12345678909", "address": "Avenida Paulista", "complement": "Sala 5",
                            "number": "1000", "district": "Bela Vista", "city": "São Paulo", "country_id": "BR",
                            "postal_code": "01310100", "state_abbr": "SP"
                          },
                          "products": [ { "name": "Vestido Aurora (M)", "quantity": "1", "unitary_value": "159.90" } ],
                          "volumes": [ { "height": 5, "width": 20, "length": 30, "weight": 0.3 } ],
                          "options": {
                            "insurance_value": 159.90, "receipt": false, "own_hand": false, "reverse": true,
                            "non_commercial": true, "tags": [ { "tag": "Troca 7 (pedido 42)" } ]
                          }
                        }
                        """, true))
                .andRespond(withSuccess("{ \"id\": \"%s\" }".formatted(ID), MediaType.APPLICATION_JSON));

        String id = emissor.adicionarAoCarrinho(new NovoEnvio(42L, 1,
                new Destinatario("Maria Cliente", "11988887777", "maria@rosie.test", "03925050", "Rua das Flores",
                        "120", null, "Jardim Iva", "São Paulo", "SP"),
                List.of(new Item("Vestido Aurora (M)", 1, new BigDecimal("159.90"))),
                new Volume(5, 20, 30, new BigDecimal("0.3")),
                new BigDecimal("159.90"), 7L));

        assertThat(id).isEqualTo(ID);
        servidor.verify();
    }

    @Test
    void linkDaEtiquetaDeVoltaEPublico() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.IMPRESSAO))
                .andExpect(content().json("{ \"mode\": \"public\", \"orders\": [\"%s\"] }".formatted(ID), true))
                .andRespond(withSuccess("{ \"url\": \"https://melhorenvio.com.br/imprimir/publico\" }",
                        MediaType.APPLICATION_JSON));

        assertThat(emissor.linkImpressaoPublico(ID)).isEqualTo("https://melhorenvio.com.br/imprimir/publico");
        servidor.verify();
    }

    @Test
    void remetenteComCnpjVaiEmCompanyDocument() {
        Remetente empresa = new Remetente("Rosie Store LTDA", "11999990000", null, "12345678000195", "123456789",
                "01310100", "Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP");
        RestClient.Builder outro = RestClient.builder();
        MockRestServiceServer servidorEmpresa = MockRestServiceServer.bindTo(outro).build();
        MelhorEnvioEmissor emissorEmpresa = new MelhorEnvioEmissor(outro, BASE, "t", "Rosie", empresa);
        servidorEmpresa.expect(requestTo(BASE + MelhorEnvioEmissor.CARRINHO))
                .andExpect(content().json("""
                        { "from": { "company_document": "12345678000195", "state_register": "123456789" } }
                        """))
                .andRespond(withSuccess("{ \"id\": \"%s\" }".formatted(ID), MediaType.APPLICATION_JSON));

        emissorEmpresa.adicionarAoCarrinho(new NovoEnvio(1L, 1,
                new Destinatario("Maria", null, "m@rosie.test", "03925050", "Rua", "1", null, "B", "C", "SP"),
                List.of(new Item("Peça", 1, BigDecimal.TEN)), new Volume(5, 20, 30, new BigDecimal("0.3")),
                BigDecimal.TEN));

        servidorEmpresa.verify();
    }

    @Test
    void compraGeraEImprimeComOIdDoEnvio() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.COMPRA))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{ \"orders\": [\"%s\"] }".formatted(ID), true))
                .andRespond(withSuccess("""
                        { "purchase": { "id": "p-1", "protocol": "PUR-1", "total": 22.9, "status": "paid" } }
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.GERACAO))
                .andExpect(content().json("{ \"orders\": [\"%s\"] }".formatted(ID), true))
                .andRespond(withSuccess("""
                        { "%s": { "status": true, "message": "Envio gerado com sucesso" } }
                        """.formatted(ID), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.IMPRESSAO))
                .andExpect(content().json("{ \"mode\": \"private\", \"orders\": [\"%s\"] }".formatted(ID), true))
                .andRespond(withSuccess("{ \"url\": \"https://sandbox.melhorenvio.com.br/imprimir/ixQLaq\" }",
                        MediaType.APPLICATION_JSON));

        emissor.comprar(ID);
        emissor.gerar(ID);
        assertThat(emissor.linkImpressao(ID)).isEqualTo("https://sandbox.melhorenvio.com.br/imprimir/ixQLaq");
        servidor.verify();
    }

    @Test
    void consultaASituacaoEORastreio() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.RASTREIO))
                .andExpect(content().json("{ \"orders\": [\"%s\"] }".formatted(ID), true))
                .andRespond(withSuccess("""
                        { "%s": { "id": "%s", "protocol": "ORD-1", "status": "generated",
                                  "tracking": null, "melhorenvio_tracking": "ME2600ABC1BR" } }
                        """.formatted(ID, ID), MediaType.APPLICATION_JSON));

        SituacaoEnvio situacao = emissor.consultar(ID);

        assertThat(situacao.etapa()).isEqualTo(Etapa.GERADO);
        // Sem o código da transportadora ainda, vale o do Melhor Envio (também rastreável)
        assertThat(situacao.codigoRastreio()).isEqualTo("ME2600ABC1BR");
    }

    @Test
    void entregueVemComODiaDaEntrega() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.RASTREIO))
                .andRespond(withSuccess("""
                        { "%s": { "id": "%s", "status": "delivered", "tracking": "AA123456789BR",
                                  "posted_at": "2026-10-14 13:55:05", "delivered_at": "2026-10-17 14:46:00" } }
                        """.formatted(ID, ID), MediaType.APPLICATION_JSON));

        SituacaoEnvio situacao = emissor.consultar(ID);

        assertThat(situacao.etapa()).isEqualTo(Etapa.GERADO);
        assertThat(situacao.entregue()).isTrue();
        assertThat(situacao.entregueEm()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void emTransitoNaoEstaEntregue() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.RASTREIO))
                .andRespond(withSuccess("""
                        { "%s": { "id": "%s", "status": "posted", "tracking": "AA123456789BR", "delivered_at": null } }
                        """.formatted(ID, ID), MediaType.APPLICATION_JSON));

        SituacaoEnvio situacao = emissor.consultar(ID);

        assertThat(situacao.entregue()).isFalse();
        assertThat(situacao.entregueEm()).isNull();
    }

    @Test
    void diaDaEntregaEmSaoPaulo() {
        // Sem fuso: já é o horário de Brasília
        assertThat(MelhorEnvioEmissor.diaDaEntrega(ID, "2026-10-17 23:30:00")).isEqualTo(LocalDate.of(2026, 10, 17));
        // Com fuso: 01:30 UTC do dia 18 ainda é 22:30 do dia 17 em São Paulo
        assertThat(MelhorEnvioEmissor.diaDaEntrega(ID, "2026-10-18T01:30:00.000000Z"))
                .isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(MelhorEnvioEmissor.diaDaEntrega(ID, null)).isNull();
        assertThat(MelhorEnvioEmissor.diaDaEntrega(ID, "ontem")).isNull();
    }

    @Test
    void situacoesDoMelhorEnvio() {
        assertThat(MelhorEnvioEmissor.etapa("pending")).isEqualTo(Etapa.NO_CARRINHO);
        assertThat(MelhorEnvioEmissor.etapa("released")).isEqualTo(Etapa.COMPRADO);
        assertThat(MelhorEnvioEmissor.etapa("posted")).isEqualTo(Etapa.GERADO);
        assertThat(MelhorEnvioEmissor.etapa("canceled")).isEqualTo(Etapa.CANCELADO);
        assertThat(MelhorEnvioEmissor.etapa("expired")).isEqualTo(Etapa.CANCELADO);
        assertThat(MelhorEnvioEmissor.etapa("paused")).isNull();
    }

    @Test
    void recusaDoMelhorEnvioVaiNaMensagem() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.COMPRA))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "message": "The given data was invalid.",
                                  "errors": { "orders": ["Saldo insuficiente para a compra."] } }
                                """));

        assertThatThrownBy(() -> emissor.comprar(ID))
                .isInstanceOf(EtiquetaFalhouException.class)
                .hasMessage("O Melhor Envio recusou a etiqueta: Saldo insuficiente para a compra.");
    }

    @Test
    void geracaoComStatusFalsoFalha() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.GERACAO))
                .andRespond(withSuccess("""
                        { "%s": { "status": false, "message": "Envio não está pago" } }
                        """.formatted(ID), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> emissor.gerar(ID))
                .isInstanceOf(EtiquetaFalhouException.class)
                .hasMessageContaining("Envio não está pago");
    }

    @Test
    void tokenRecusadoViraIndisponivel() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.RASTREIO))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{ \"message\": \"Unauthenticated.\" }"));

        assertThatThrownBy(() -> emissor.consultar(ID)).isInstanceOf(EtiquetaIndisponivelException.class);
    }

    @Test
    void foraDoArOuLentoFalhaComMensagemGenerica() {
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.IMPRESSAO)).andRespond(withServerError());
        servidor.expect(requestTo(BASE + MelhorEnvioEmissor.IMPRESSAO))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> emissor.linkImpressao(ID))
                .isInstanceOf(EtiquetaFalhouException.class).hasMessage(MelhorEnvioEmissor.FALHA_GENERICA);
        assertThatThrownBy(() -> emissor.linkImpressao(ID))
                .isInstanceOf(EtiquetaFalhouException.class).hasMessage(MelhorEnvioEmissor.FALHA_GENERICA);
    }
}
