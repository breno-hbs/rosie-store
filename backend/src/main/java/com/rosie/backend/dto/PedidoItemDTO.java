package com.rosie.backend.dto;

import com.rosie.backend.model.PedidoItem;

import java.math.BigDecimal;

// Peça do pedido com o preço do momento da compra (não o atual do produto)
public record PedidoItemDTO(
        Long produtoId,
        String nome,
        String imagemPrincipal,
        String tamanho,
        Integer quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal
) {

    public static PedidoItemDTO from(PedidoItem item) {
        CarrinhoProdutoDTO produto = CarrinhoProdutoDTO.from(item.getProduto());
        return new PedidoItemDTO(produto.id(), produto.nome(), produto.imagemPrincipal(), item.getTamanho().name(),
                item.getQuantidade(), item.getPrecoUnitario(),
                item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
    }
}
