package com.rosie.backend.dto;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;

public record UsuarioResumoDTO(Long id, String nome, Role role) {

    public static UsuarioResumoDTO from(Usuario usuario) {
        return new UsuarioResumoDTO(usuario.getId(), usuario.getNome(), usuario.getRole());
    }
}
