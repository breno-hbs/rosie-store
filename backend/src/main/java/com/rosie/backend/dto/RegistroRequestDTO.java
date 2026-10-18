package com.rosie.backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Cadastro de cliente (RF08). A senha tem no máximo 72 caracteres porque o BCrypt ignora o resto.
public record RegistroRequestDTO(
        @NotBlank(message = "Informe o nome")
        @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres")
        String nome,

        // O @Email sozinho aceita "maria@email"; aqui o domínio precisa ter ponto
        @NotBlank(message = "Informe o e-mail")
        @Email(regexp = ".+@.+\\..+", message = "E-mail inválido")
        @Size(max = 150, message = "O e-mail pode ter no máximo 150 caracteres")
        String email,

        @NotBlank(message = "Informe a senha")
        @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres")
        String senha,

        @NotBlank(message = "Informe o telefone")
        @Pattern(regexp = "\\d{10,11}", message = "Telefone inválido: informe DDD e número, só com dígitos")
        String telefone,

        @NotNull(message = "Aceite a política de privacidade para criar a conta")
        @AssertTrue(message = "Aceite a política de privacidade para criar a conta")
        Boolean aceitePrivacidade
) {

    // Espaços nas pontas do e-mail (comuns no autocompletar do celular) não invalidam o cadastro
    public RegistroRequestDTO {
        email = email == null ? null : email.trim();
    }
}
