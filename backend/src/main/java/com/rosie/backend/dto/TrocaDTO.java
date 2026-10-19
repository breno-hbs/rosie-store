package com.rosie.backend.dto;

import com.rosie.backend.model.Troca;

import java.time.LocalDateTime;

// Troca vista pela cliente (POST e GET /trocas, #27): a peça, o motivo, quem paga o frete de volta e em que
// pé está, com o recado da Roseane
public record TrocaDTO(
        Long id,
        Long pedidoId,
        Long produtoId,
        String nome,
        String imagemPrincipal,
        String tamanho,
        Integer quantidade,
        String motivo,
        String freteConta,
        String status,
        String observacao,
        String resposta,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TrocaDTO from(Troca troca) {
        CarrinhoProdutoDTO produto = CarrinhoProdutoDTO.from(troca.getProduto());
        return new TrocaDTO(
                troca.getId(),
                troca.getPedido().getId(),
                produto.id(),
                produto.nome(),
                produto.imagemPrincipal(),
                troca.getTamanho().name(),
                troca.getQuantidade(),
                troca.getMotivo().name(),
                troca.getFreteConta().name(),
                troca.getStatus().name(),
                troca.getObservacao(),
                troca.getResposta(),
                troca.getCreatedAt(),
                troca.getUpdatedAt()
        );
    }
}
