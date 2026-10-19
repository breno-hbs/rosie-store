package com.rosie.backend.dto;

import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Uma linha da lista de pedidos do painel (GET /admin/pedidos, #25)
public record PedidoAdminResumoDTO(
        Long id,
        String status,
        ClientePedidoDTO cliente,
        Integer quantidadePecas,
        BigDecimal valorTotal,
        String codigoRastreio,
        LocalDateTime createdAt
) {

    public static PedidoAdminResumoDTO from(Pedido pedido) {
        return new PedidoAdminResumoDTO(
                pedido.getId(),
                pedido.getStatus().name(),
                ClientePedidoDTO.from(pedido.getUsuario()),
                pedido.getItens().stream().mapToInt(PedidoItem::getQuantidade).sum(),
                pedido.getValorTotal(),
                pedido.getCodigoRastreio(),
                pedido.getCreatedAt()
        );
    }
}
