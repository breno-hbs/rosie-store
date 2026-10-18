package com.rosie.backend.config;

import com.cloudinary.Cloudinary;
import com.rosie.backend.service.ArmazenamentoImagens;
import com.rosie.backend.service.ArmazenamentoIndisponivel;
import com.rosie.backend.service.CloudinaryArmazenamento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Storage das fotos dos produtos. Sem CLOUDINARY_URL a API sobe do mesmo jeito (dev local, CI)
// e só o upload responde 503 ARMAZENAMENTO_INDISPONIVEL.
@Configuration
@Slf4j
public class ArmazenamentoConfig {

    @Bean
    public ArmazenamentoImagens armazenamentoImagens(@Value("${rosie.cloudinary.url:}") String url) {
        if (url == null || url.isBlank()) {
            log.warn("CLOUDINARY_URL não definida: o envio de fotos do painel vai responder 503.");
            return new ArmazenamentoIndisponivel();
        }
        // Formato cloudinary://<api_key>:<api_secret>@<cloud_name>
        return new CloudinaryArmazenamento(new Cloudinary(url.trim()));
    }
}
