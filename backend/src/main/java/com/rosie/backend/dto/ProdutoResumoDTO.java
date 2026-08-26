package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;

import java.math.BigDecimal;
import java.util.Comparator;

public record ProdutoResumoDTO(
        Long id,
        String nome,
        BigDecimal preco,
        CategoriaResumoDTO categoria,
        String imagemPrincipal,
        boolean disponivel
) {

    public static ProdutoResumoDTO from(Produto produto) {
        String imagemPrincipal = produto.getImagens().stream()
                .min(Comparator.comparing(ProdutoImagem::getOrdem))
                .map(ProdutoImagem::getUrl)
                .orElse(null);

        return new ProdutoResumoDTO(
                produto.getId(),
                produto.getNome(),
                produto.getPreco(),
                CategoriaResumoDTO.from(produto.getCategoria()),
                imagemPrincipal,
                produto.getAtivo()
        );
    }
}
