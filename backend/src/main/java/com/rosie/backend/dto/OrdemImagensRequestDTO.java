package com.rosie.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Body de PUT /admin/produtos/{id}/imagens/ordem: os ids de todas as fotos do produto, a principal primeiro
public record OrdemImagensRequestDTO(
        @NotEmpty(message = "Informe a ordem das fotos")
        List<@NotNull(message = "Id de foto vazio") Long> imagens
) {
}
