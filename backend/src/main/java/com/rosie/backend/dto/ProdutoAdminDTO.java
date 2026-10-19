package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// Detalhe do produto no painel: o mesmo do ProdutoDetalheDTO público, mais ativo, destaque e o pacote do frete
// (peso e medidas, só do painel).
// As imagens vêm com id (para remover e reordenar), não só a URL.
public record ProdutoAdminDTO(
        Long id,
        String nome,
        String descricao,
        String composicao,
        BigDecimal preco,
        CategoriaResumoDTO categoria,
        List<ProdutoImagemDTO> imagens,
        List<EstoqueDTO> estoque,
        boolean ativo,
        boolean destaque,
        PacoteDTO pacote
) {

    public static ProdutoAdminDTO from(Produto produto) {
        ProdutoDetalheDTO detalhe = ProdutoDetalheDTO.from(produto);
        List<ProdutoImagemDTO> imagens = produto.getImagens().stream()
                .sorted(Comparator.comparing(ProdutoImagem::getOrdem))
                .map(ProdutoImagemDTO::from)
                .toList();

        return new ProdutoAdminDTO(
                detalhe.id(),
                detalhe.nome(),
                detalhe.descricao(),
                detalhe.composicao(),
                detalhe.preco(),
                detalhe.categoria(),
                imagens,
                detalhe.estoque(),
                produto.getAtivo(),
                produto.getDestaque(),
                PacoteDTO.from(produto)
        );
    }
}
