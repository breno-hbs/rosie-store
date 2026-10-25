package com.rosie.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST /auth/redefinir-senha (#93): o token do link e a senha nova, com as regras do cadastro. Token ausente ou
// vazio responde como token inválido (400 TOKEN_INVALIDO), para a página tratar tudo igual.
public record RedefinicaoSenhaRequestDTO(
        String token,

        @NotBlank(message = "Informe a nova senha")
        @Size(min = 8, max = 72, message = "A senha precisa ter de 8 a 72 caracteres")
        String novaSenha
) {
}
