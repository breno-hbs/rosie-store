package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;

import java.math.BigDecimal;
import java.util.List;

// Linha das telas "Admin — Lista de Produtos" (estoqueTotal) e "Admin — Gestão de Estoque"
// (estoque por tamanho). Inclui os inativos.
public record ProdutoAdminResumoDTO(
        Long id,
        String nome,
        BigDecimal preco,
        CategoriaResumoDTO categoria,
        int estoqueTotal,
        List<EstoqueDTO> estoque,
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
                EstoqueDTO.ordenados(produto.getEstoque()),
                produto.getAtivo(),
                produto.getDestaque()
        );
    }
}
