package com.rosie.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// PUT /conta/senha (#26): a senha atual confirma que é a dona da conta, não alguém com o celular dela
public record SenhaRequestDTO(
        @NotBlank(message = "Informe a senha atual")
        String senhaAtual,

        @NotBlank(message = "Informe a nova senha")
        @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres")
        String novaSenha
) {
}
