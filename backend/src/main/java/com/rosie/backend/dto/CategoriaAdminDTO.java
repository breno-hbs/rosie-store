package com.rosie.backend.dto;

// Categoria no painel admin: com a quantidade de produtos (ativos e inativos) ligados a ela
public record CategoriaAdminDTO(Long id, String nome, long quantidadeProdutos) {
}
