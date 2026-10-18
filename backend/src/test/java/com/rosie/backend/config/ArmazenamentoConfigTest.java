package com.rosie.backend.config;

import com.rosie.backend.service.ArmazenamentoIndisponivel;
import com.rosie.backend.service.CloudinaryArmazenamento;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Escolha do storage pela CLOUDINARY_URL (só monta o cliente: nada aqui chama o Cloudinary)
class ArmazenamentoConfigTest {

    private final ArmazenamentoConfig config = new ArmazenamentoConfig();

    @Test
    void comCloudinaryUrlUsaOCloudinary() {
        assertThat(config.armazenamentoImagens("cloudinary://chave:segredo@rosie-teste"))
                .isInstanceOf(CloudinaryArmazenamento.class);
    }

    @Test
    void semCloudinaryUrlSobeComOStorageIndisponivel() {
        assertThat(config.armazenamentoImagens("")).isInstanceOf(ArmazenamentoIndisponivel.class);
        assertThat(config.armazenamentoImagens("  ")).isInstanceOf(ArmazenamentoIndisponivel.class);
        assertThat(config.armazenamentoImagens(null)).isInstanceOf(ArmazenamentoIndisponivel.class);
    }
}
