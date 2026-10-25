package com.rosie.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST /auth/esqueci-senha (#93): só o e-mail, da cliente ou do admin
public record EsqueciSenhaRequestDTO(
        @NotBlank(message = "Informe o e-mail")
        @Email(regexp = ".+@.+\\..+", message = "E-mail inválido")
        @Size(max = 150, message = "O e-mail pode ter no máximo 150 caracteres")
        String email
) {

    // Espaços nas pontas (autocompletar do celular) não invalidam o pedido, como no cadastro
    public EsqueciSenhaRequestDTO {
        email = email == null ? null : email.trim();
    }
}
