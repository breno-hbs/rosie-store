package com.rosie.backend.config;

import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;
import com.rosie.backend.service.CalculadoraFrete.PecaFrete;
import com.rosie.backend.service.FreteIndisponivel;
import com.rosie.backend.service.FreteSimulado;
import com.rosie.backend.service.MelhorEnvioCalculadora;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Escolha da calculadora de frete pelas variáveis (só monta o cliente: nada aqui chama o Melhor Envio)
class FreteConfigTest {

    private static final String URL = "https://sandbox.melhorenvio.com.br";
    private static final String USER_AGENT = "Rosie (teste@rosie.test)";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final FreteConfig config = new FreteConfig();

    @Test
    void comTokenECepDeOrigemUsaOMelhorEnvio() {
        assertThat(config.calculadoraFrete(false, "token", URL, USER_AGENT, "01310-100", TIMEOUT))
                .isInstanceOf(MelhorEnvioCalculadora.class);
    }

    @Test
    void semTokenOuSemCepDeOrigemSobeComOFreteIndisponivel() {
        assertThat(config.calculadoraFrete(false, "", URL, USER_AGENT, "01310100", TIMEOUT))
                .isInstanceOf(FreteIndisponivel.class);
        assertThat(config.calculadoraFrete(false, null, URL, USER_AGENT, "01310100", TIMEOUT))
                .isInstanceOf(FreteIndisponivel.class);
        assertThat(config.calculadoraFrete(false, "token", URL, USER_AGENT, "", TIMEOUT))
                .isInstanceOf(FreteIndisponivel.class);
        assertThat(config.calculadoraFrete(false, "token", URL, USER_AGENT, "0131", TIMEOUT))
                .isInstanceOf(FreteIndisponivel.class);
    }

    @Test
    void modoSimuladoNaoPrecisaDeToken() {
        assertThat(config.calculadoraFrete(true, "", URL, USER_AGENT, "", TIMEOUT))
                .isInstanceOf(FreteSimulado.class);
    }

    @Test
    void freteSimuladoTemTresOpcoesEUmCepSemOpcoes() {
        FreteSimulado simulado = new FreteSimulado();
        List<PecaFrete> duasPecas = List.of(
                new PecaFrete("produto-1-M", 2, new BigDecimal("100.00"), new BigDecimal("0.3"), 20, 5, 30));

        assertThat(simulado.calcular("03925050", duasPecas)).extracting(OpcaoFrete::servico)
                .containsExactly("PAC", "SEDEX", ".Package");
        assertThat(simulado.calcular("03925050", duasPecas).get(0).preco()).isEqualByComparingTo("21.40");
        assertThat(simulado.calcular("99999999", duasPecas)).isEmpty();
    }
}
