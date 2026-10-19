package com.rosie.backend.dto;

import com.rosie.backend.model.Usuario;

import java.time.LocalDateTime;

// Dados pessoais da cliente na Minha Conta (#26). O e-mail é só leitura (é o login). privacidadeVersao e
// privacidadeAceitaEm (#31) mostram qual versão da política ela aceitou e quando.
public record ContaDTO(
        Long id,
        String nome,
        String email,
        String telefone,
        String privacidadeVersao,
        LocalDateTime privacidadeAceitaEm
) {

    public static ContaDTO from(Usuario usuario) {
        return new ContaDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone(),
                usuario.getPrivacidadeVersao(), usuario.getPrivacidadeAceitaEm());
    }
}
