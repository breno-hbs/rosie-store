package com.rosie.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "Informe o e-mail") @Email(message = "E-mail inválido") String email,
        @NotBlank(message = "Informe a senha") String senha
) {

    // Espaços nas pontas do e-mail (comuns no autocompletar do celular) não invalidam o login
    public LoginRequestDTO {
        email = email == null ? null : email.trim();
    }
}
