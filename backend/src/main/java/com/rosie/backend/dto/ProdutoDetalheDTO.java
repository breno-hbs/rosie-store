package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record ProdutoDetalheDTO(
        Long id,
        String nome,
        String descricao,
        BigDecimal preco,
        CategoriaResumoDTO categoria,
        List<String> imagens,
        List<EstoqueDTO> estoque
) {

    public static ProdutoDetalheDTO from(Produto produto) {
        List<String> urls = produto.getImagens().stream()
                .sorted(Comparator.comparing(ProdutoImagem::getOrdem))
                .map(ProdutoImagem::getUrl)
                .toList();

        List<EstoqueDTO> estoque = produto.getEstoque().stream()
                .map(EstoqueDTO::from)
                .toList();

        return new ProdutoDetalheDTO(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getPreco(),
                CategoriaResumoDTO.from(produto.getCategoria()),
                urls,
                estoque
        );
    }
}
