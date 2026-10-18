package com.rosie.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Body de PUT /carrinho/itens/{itemId}: a nova quantidade do item (para tirar, DELETE)
public record QuantidadeRequestDTO(
        @NotNull(message = "Informe a quantidade")
        @Min(value = 1, message = "A quantidade deve ser pelo menos 1")
        @Max(value = 99, message = "A quantidade deve ser no máximo 99")
        Integer quantidade
) {
}
