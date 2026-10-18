package com.rosie.backend.config;

import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.service.GatewayPagamento;
import com.rosie.backend.service.GatewayPagamento.NovaPreferencia;
import com.rosie.backend.service.GatewayPagamento.StatusPagamento;
import com.rosie.backend.service.MercadoPagoGateway;
import com.rosie.backend.service.PagamentoIndisponivel;
import com.rosie.backend.service.PagamentoSimulado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Escolha do gateway de pagamento pelas variáveis (só monta o cliente: nada aqui chama o Mercado Pago)
class PagamentoConfigTest {

    private static final String MP = "https://api.mercadopago.com";
    private static final String API = "https://api.rosiestore.com.br";
    private static final String LOJA = "https://rosiestore.com.br";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final PagamentoConfig config = new PagamentoConfig();

    @Test
    void comAsTresVariaveisUsaOMercadoPago() {
        assertThat(config.gatewayPagamento(false, "TEST-token", "segredo", MP, API, LOJA, TIMEOUT))
                .isInstanceOf(MercadoPagoGateway.class);
    }

    @Test
    void faltandoAlgumaVariavelSobeComOPagamentoIndisponivel() {
        assertThat(config.gatewayPagamento(false, "", "segredo", MP, API, LOJA, TIMEOUT))
                .isInstanceOf(PagamentoIndisponivel.class);
        assertThat(config.gatewayPagamento(false, "TEST-token", null, MP, API, LOJA, TIMEOUT))
                .isInstanceOf(PagamentoIndisponivel.class);
        assertThat(config.gatewayPagamento(false, "TEST-token", "segredo", MP, "", LOJA, TIMEOUT))
                .isInstanceOf(PagamentoIndisponivel.class);
        // O Mercado Pago só manda webhook para https
        assertThat(config.gatewayPagamento(false, "TEST-token", "segredo", MP, "http://localhost:8080", LOJA, TIMEOUT))
                .isInstanceOf(PagamentoIndisponivel.class);
    }

    @Test
    void modoSimuladoNaoPrecisaDeCredencial() {
        GatewayPagamento gateway = config.gatewayPagamento(true, "", "", MP, "", "http://localhost:5173", TIMEOUT);
        assertThat(gateway).isInstanceOf(PagamentoSimulado.class);

        PagamentoSimulado simulado = (PagamentoSimulado) gateway;
        assertThat(simulado.criarPreferencia(new NovaPreferencia(7L, FormaPagamento.PIX, "Maria", "m@rosie.test",
                List.of())).urlPagamento()).isEqualTo("http://localhost:5173/pedido/7/pagamento-simulado");

        String id = simulado.registrar(7L, StatusPagamento.APROVADO, new BigDecimal("10.00"));
        assertThat(simulado.buscarPagamento(id).referenciaExterna()).isEqualTo("7");
    }
}
