package com.rosie.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Body de PUT /admin/categorias/{id}/medidas: a tabela inteira, na ordem de exibição.
// Substitui as linhas que existiam; lista vazia apaga a tabela.
public record TabelaMedidasRequestDTO(
        @NotNull(message = "Informe as medidas")
        @Size(max = 20, message = "A tabela pode ter no máximo 20 medidas")
        List<@NotNull(message = "Medida vazia") @Valid MedidaDTO> medidas
) {
}
