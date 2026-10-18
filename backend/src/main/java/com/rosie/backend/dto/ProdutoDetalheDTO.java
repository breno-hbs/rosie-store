package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.ProdutoImagem;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record ProdutoDetalheDTO(
        Long id,
        String nome,
        String descricao,
        String composicao,
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

        // Na ordem do enum Tamanho (P, M, G, GG), a mesma dos botões na Página de Produto
        List<EstoqueDTO> estoque = produto.getEstoque().stream()
                .sorted(Comparator.comparing(ProdutoEstoque::getTamanho))
                .map(EstoqueDTO::from)
                .toList();

        return new ProdutoDetalheDTO(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getComposicao(),
                produto.getPreco(),
                CategoriaResumoDTO.from(produto.getCategoria()),
                urls,
                estoque
        );
    }
}
