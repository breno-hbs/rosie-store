package com.rosie.backend.dto;

import com.rosie.backend.model.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Linha da tabela "Meus Pedidos" (GET /pedidos, #26). O detalhe vem do GET /pedidos/{id} (PedidoDTO).
public record PedidoResumoDTO(
        Long id,
        String status,
        BigDecimal valorTotal,
        LocalDateTime createdAt
) {

    public static PedidoResumoDTO from(Pedido pedido) {
        return new PedidoResumoDTO(pedido.getId(), pedido.getStatus().name(), pedido.getValorTotal(),
                pedido.getCreatedAt());
    }
}
