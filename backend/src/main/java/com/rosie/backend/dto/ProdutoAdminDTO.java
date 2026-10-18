package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;

import java.math.BigDecimal;
import java.util.List;

// Detalhe do produto no painel: o mesmo do ProdutoDetalheDTO público, mais ativo e destaque
public record ProdutoAdminDTO(
        Long id,
        String nome,
        String descricao,
        String composicao,
        BigDecimal preco,
        CategoriaResumoDTO categoria,
        List<String> imagens,
        List<EstoqueDTO> estoque,
        boolean ativo,
        boolean destaque
) {

    public static ProdutoAdminDTO from(Produto produto) {
        ProdutoDetalheDTO detalhe = ProdutoDetalheDTO.from(produto);

        return new ProdutoAdminDTO(
                detalhe.id(),
                detalhe.nome(),
                detalhe.descricao(),
                detalhe.composicao(),
                detalhe.preco(),
                detalhe.categoria(),
                detalhe.imagens(),
                detalhe.estoque(),
                produto.getAtivo(),
                produto.getDestaque()
        );
    }
}
