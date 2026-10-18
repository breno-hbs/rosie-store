package com.rosie.backend.dto;

import com.rosie.backend.model.Usuario;

// Dados pessoais da cliente na Minha Conta (#26). O e-mail é só leitura (é o login).
public record ContaDTO(
        Long id,
        String nome,
        String email,
        String telefone
) {

    public static ContaDTO from(Usuario usuario) {
        return new ContaDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone());
    }
}
