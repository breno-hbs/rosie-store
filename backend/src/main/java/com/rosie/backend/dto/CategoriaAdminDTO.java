package com.rosie.backend.dto;

// Categoria no painel admin: com o slug da página na loja (#88) e a quantidade de produtos (ativos e inativos)
// ligados a ela
public record CategoriaAdminDTO(Long id, String nome, String slug, long quantidadeProdutos) {
}
