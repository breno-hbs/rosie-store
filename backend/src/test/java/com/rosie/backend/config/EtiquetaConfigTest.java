package com.rosie.backend.config;

import com.rosie.backend.service.EmissorEtiqueta;
import com.rosie.backend.service.EmissorEtiqueta.Etapa;
import com.rosie.backend.service.EtiquetaIndisponivel;
import com.rosie.backend.service.EtiquetaSimulada;
import com.rosie.backend.service.MelhorEnvioEmissor;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Escolha do emissor de etiqueta pelas variáveis (só monta o cliente: nada aqui chama o Melhor Envio)
class EtiquetaConfigTest {

    private static final String URL = "https://sandbox.melhorenvio.com.br";
    private static final String LOJA = "http://localhost:5173";

    private final EtiquetaConfig config = new EtiquetaConfig();

    @Test
    void comTokenCepERemetenteCompletoUsaOMelhorEnvio() {
        assertThat(montar(false, "token", "01310-100", "123.456.789-09", "SP"))
                .isInstanceOf(MelhorEnvioEmissor.class);
        // CNPJ também serve
        assertThat(montar(false, "token", "01310100", "12.345.678/0001-95", "sp"))
                .isInstanceOf(MelhorEnvioEmissor.class);
    }

    @Test
    void faltandoTokenCepDocumentoOuUfSobeComAEtiquetaIndisponivel() {
        assertThat(montar(false, "", "01310100", "12345678909", "SP")).isInstanceOf(EtiquetaIndisponivel.class);
        assertThat(montar(false, "token", "", "12345678909", "SP")).isInstanceOf(EtiquetaIndisponivel.class);
        assertThat(montar(false, "token", "01310100", "1234", "SP")).isInstanceOf(EtiquetaIndisponivel.class);
        assertThat(montar(false, "token", "01310100", "12345678909", "")).isInstanceOf(EtiquetaIndisponivel.class);
    }

    @Test
    void modoSimuladoNaoPrecisaDeNada() {
        EmissorEtiqueta simulado = montar(true, "", "", "", "");
        assertThat(simulado).isInstanceOf(EtiquetaSimulada.class);

        String id = simulado.adicionarAoCarrinho(null);
        simulado.comprar(id);
        simulado.gerar(id);
        assertThat(simulado.consultar(id).etapa()).isEqualTo(Etapa.GERADO);
        assertThat(simulado.consultar(id).codigoRastreio()).matches("SIM\\d{8}BR");
        assertThat(simulado.linkImpressao(id)).isEqualTo(LOJA + "/etiqueta-simulada.html?envio=" + id);
    }

    private EmissorEtiqueta montar(boolean simulado, String token, String cep, String documento, String uf) {
        return config.emissorEtiqueta(simulado, token, URL, "Rosie (teste@rosie.test)", cep,
                "Rosie Store", "(11) 99999-0000", "contato@rosie.test", documento, "",
                "Avenida Paulista", "1000", "", "Bela Vista", "São Paulo", uf, LOJA, Duration.ofSeconds(20));
    }
}
