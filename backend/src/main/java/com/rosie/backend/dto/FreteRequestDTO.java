package com.rosie.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// CEP com ou sem hífen: "03925-050" ou "03925050"
public record FreteRequestDTO(
        @NotBlank(message = "Informe o CEP")
        @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido: use os 8 números, ex.: 03925-050")
        String cep
) {
}
