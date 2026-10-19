package com.rosie.backend.dto;

import jakarta.validation.constraints.NotBlank;

// POST /conta/exclusao (#31): a senha atual confirma que é a dona da conta pedindo a exclusão
public record ExclusaoContaRequestDTO(
        @NotBlank(message = "Informe a sua senha")
        String senha
) {
}
