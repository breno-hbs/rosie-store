package com.rosie.backend.dto;

import com.rosie.backend.model.Pedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Linha da tabela "Meus Pedidos" (GET /pedidos, #26). O detalhe vem do GET /pedidos/{id} (PedidoDTO).
// prazoTroca (#27): último dia para pedir troca, só em pedido entregue (a tela de troca lista esses).
public record PedidoResumoDTO(
        Long id,
        String status,
        BigDecimal valorTotal,
        LocalDateTime createdAt,
        LocalDate prazoTroca
) {

    public static PedidoResumoDTO from(Pedido pedido, LocalDate prazoTroca) {
        return new PedidoResumoDTO(pedido.getId(), pedido.getStatus().name(), pedido.getValorTotal(),
                pedido.getCreatedAt(), prazoTroca);
    }
}
