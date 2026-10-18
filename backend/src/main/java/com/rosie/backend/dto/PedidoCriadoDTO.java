package com.rosie.backend.dto;

import com.rosie.backend.model.Pedido;

import java.math.BigDecimal;

// Resposta do POST /pedidos: o front manda a cliente para urlPagamento (página do Mercado Pago)
public record PedidoCriadoDTO(
        Long id,
        String status,
        BigDecimal subtotal,
        BigDecimal valorFrete,
        BigDecimal valorTotal,
        String urlPagamento
) {

    public static PedidoCriadoDTO from(Pedido pedido) {
        return new PedidoCriadoDTO(pedido.getId(), pedido.getStatus().name(),
                pedido.getValorTotal().subtract(pedido.getValorFrete()), pedido.getValorFrete(),
                pedido.getValorTotal(), pedido.getUrlPagamento());
    }
}
