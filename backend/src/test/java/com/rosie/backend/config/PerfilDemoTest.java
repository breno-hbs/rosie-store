package com.rosie.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Perfil de demonstração (#77): liga só os integradores simulados, sem credencial real, e nunca junto com o prod
class PerfilDemoTest {

    @Test
    void sobeSozinho() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("demo");
        new PerfilDemo(ambiente);
    }

    @Test
    void naoSobeJuntoComOProd() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("prod", "demo");
        assertThatThrownBy(() -> new PerfilDemo(ambiente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void ligaOsSimuladosSemCredencialRealNumBancoProprio() throws IOException {
        Properties demo = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-demo.properties"));

        assertThat(demo.getProperty("rosie.frete.simulado")).isEqualTo("true");
        assertThat(demo.getProperty("rosie.pagamento.simulado")).isEqualTo("true");
        assertThat(demo.getProperty("rosie.frete.melhor-envio.token")).isEmpty();
        assertThat(demo.getProperty("rosie.pagamento.mercado-pago.access-token")).isEmpty();
        assertThat(demo.getProperty("rosie.pagamento.mercado-pago.segredo-webhook")).isEmpty();
        assertThat(demo.getProperty("rosie.email.resend.api-key")).isEmpty();
        assertThat(demo.getProperty("spring.datasource.url")).contains("/rosie_demo?").doesNotContain("/rosie_db");
    }
}
