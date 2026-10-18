package com.rosie.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST e PUT /admin/categorias. O service tira os espaços das pontas antes de salvar.
public record CategoriaRequestDTO(
        @NotBlank(message = "Informe o nome da categoria")
        @Size(max = 80, message = "O nome da categoria pode ter no máximo 80 caracteres")
        String nome
) {
}
