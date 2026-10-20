package com.rosie.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// PUT /conta/email (#61): o e-mail novo segue as regras do cadastro, e a senha atual confirma que é a dona da conta
public record EmailRequestDTO(
        // O @Email sozinho aceita "maria@email"; aqui o domínio precisa ter ponto (como no cadastro)
        @NotBlank(message = "Informe o novo e-mail")
        @Email(regexp = ".+@.+\\..+", message = "E-mail inválido")
        @Size(max = 150, message = "O e-mail pode ter no máximo 150 caracteres")
        String novoEmail,

        @NotBlank(message = "Informe a senha atual")
        String senhaAtual
) {

    // Espaços nas pontas (comuns no autocompletar do celular) não invalidam o pedido
    public EmailRequestDTO {
        novoEmail = novoEmail == null ? null : novoEmail.trim();
    }
}
