package com.rosie.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Body de PUT /admin/produtos/{id}/estoque: só os tamanhos que mudam; os outros ficam como estão
public record EstoqueRequestDTO(
        @NotEmpty(message = "Informe ao menos um tamanho")
        @Valid
        List<@NotNull(message = "Item de estoque vazio") @Valid EstoqueDTO> estoque
) {
}
