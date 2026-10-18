package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;

import java.util.List;

// Resposta de PUT /admin/produtos/{id}/estoque: o estoque completo do produto, em P, M, G, GG
public record EstoqueProdutoDTO(
        Long produtoId,
        List<EstoqueDTO> estoque
) {

    public static EstoqueProdutoDTO from(Produto produto) {
        return new EstoqueProdutoDTO(produto.getId(), EstoqueDTO.ordenados(produto.getEstoque()));
    }
}
