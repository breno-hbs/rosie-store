package com.rosie.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Body de PUT /admin/banners/ordem: os ids de todos os banners, o primeiro do carrossel primeiro
public record OrdemBannersRequestDTO(
        @NotEmpty(message = "Informe a ordem dos banners")
        List<@NotNull(message = "Id de banner vazio") Long> banners
) {
}
