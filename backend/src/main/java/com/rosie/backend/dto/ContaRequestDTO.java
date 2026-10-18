package com.rosie.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PUT /conta (#26): mesmas regras de nome e telefone do cadastro (RegistroRequestDTO)
public record ContaRequestDTO(
        @NotBlank(message = "Informe o nome")
        @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres")
        String nome,

        @NotBlank(message = "Informe o telefone")
        @Pattern(regexp = "\\d{10,11}", message = "Telefone inválido: informe DDD e número, só com dígitos")
        String telefone
) {
}
