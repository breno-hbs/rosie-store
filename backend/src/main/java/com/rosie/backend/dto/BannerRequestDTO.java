package com.rosie.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// Parte "dados" (JSON) do multipart de POST e PUT /admin/banners. Textos vazios viram nulos no service;
// o link é conferido pelo LinkBanner. inicio e fim no horário de São Paulo, sem fuso ("2026-12-01T00:00").
// ativo nulo = true. removerImagemCelular (só no PUT) volta a usar a imagem de desktop no celular.
public record BannerRequestDTO(
        @Size(max = 80, message = "O título pode ter no máximo 80 caracteres")
        String titulo,

        @Size(max = 200, message = "O texto pode ter no máximo 200 caracteres")
        String texto,

        @Size(max = 30, message = "O texto do botão pode ter no máximo 30 caracteres")
        String textoBotao,

        @Size(max = 300, message = "O link pode ter no máximo 300 caracteres")
        String link,

        @NotBlank(message = "Descreva a imagem do banner (texto alternativo)")
        @Size(max = 200, message = "O texto alternativo pode ter no máximo 200 caracteres")
        String textoAlternativo,

        Boolean ativo,

        LocalDateTime inicio,

        LocalDateTime fim,

        Boolean removerImagemCelular
) {
}
