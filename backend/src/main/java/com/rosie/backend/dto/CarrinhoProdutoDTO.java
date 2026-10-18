package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.service.UrlEntregaImagem;

import java.math.BigDecimal;
import java.util.Comparator;

// Produto dentro de um item do carrinho, com o preço atual (não o do momento em que entrou)
public record CarrinhoProdutoDTO(
        Long id,
        String nome,
        BigDecimal preco,
        String imagemPrincipal
) {

    public static CarrinhoProdutoDTO from(Produto produto) {
        String imagemPrincipal = produto.getImagens().stream()
                .min(Comparator.comparing(ProdutoImagem::getOrdem))
                .map(UrlEntregaImagem::de)
                .orElse(null);

        return new CarrinhoProdutoDTO(produto.getId(), produto.getNome(), produto.getPreco(), imagemPrincipal);
    }
}
