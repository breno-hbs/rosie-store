package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;

import java.math.BigDecimal;

// Linha da tela "Admin — Lista de Produtos": inclui os inativos, com o estoque somado
public record ProdutoAdminResumoDTO(
        Long id,
        String nome,
        BigDecimal preco,
        CategoriaResumoDTO categoria,
        int estoqueTotal,
        boolean ativo,
        boolean destaque
) {

    public static ProdutoAdminResumoDTO from(Produto produto) {
        int estoqueTotal = produto.getEstoque().stream()
                .mapToInt(ProdutoEstoque::getQuantidade)
                .sum();

        return new ProdutoAdminResumoDTO(
                produto.getId(),
                produto.getNome(),
                produto.getPreco(),
                CategoriaResumoDTO.from(produto.getCategoria()),
                estoqueTotal,
                produto.getAtivo(),
                produto.getDestaque()
        );
    }
}
