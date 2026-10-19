package com.rosie.backend.dto;

import com.rosie.backend.model.Usuario;

// Quem comprou, no painel de pedidos (#25): para a Roseane falar com a cliente se precisar
public record ClientePedidoDTO(Long id, String nome, String email, String telefone) {

    public static ClientePedidoDTO from(Usuario usuario) {
        return new ClientePedidoDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone());
    }
}
