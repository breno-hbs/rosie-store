package com.rosie.backend.dto;

import com.rosie.backend.model.StatusTroca;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Body de PUT /admin/trocas/{id}/status (#27). resposta é o recado que a cliente lê na Minha Conta; sem
// ela, fica o recado anterior.
public record TrocaStatusRequestDTO(
        @NotNull(message = "Informe o status")
        StatusTroca status,

        @Size(max = 1000, message = "A resposta pode ter até 1000 caracteres")
        String resposta
) {
}
