package com.rosie.backend.dto;

import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// GET /pedidos/{id} (#24): as páginas de retorno do Mercado Pago mostram o status daqui, não da URL.
// urlPagamento só vem enquanto ainda dá para pagar (aguardando ou recusado). dataEntrega e prazoTroca (último
// dia para pedir troca, #27) só vêm depois que a Roseane marca o pedido como entregue.
public record PedidoDTO(
        Long id,
        String status,
        String formaPagamento,
        BigDecimal subtotal,
        BigDecimal valorFrete,
        BigDecimal valorTotal,
        String transportadora,
        String servicoFrete,
        Integer prazoFreteDias,
        String codigoRastreio,
        LocalDate dataEntrega,
        LocalDate prazoTroca,
        String urlPagamento,
        LocalDateTime createdAt,
        EnderecoDTO endereco,
        List<PedidoItemDTO> itens
) {

    public static PedidoDTO from(Pedido pedido, LocalDate prazoTroca) {
        boolean pagavel = pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO
                || pedido.getStatus() == StatusPedido.PAGAMENTO_RECUSADO;
        return new PedidoDTO(
                pedido.getId(),
                pedido.getStatus().name(),
                pedido.getFormaPagamento() == null ? null : pedido.getFormaPagamento().name(),
                pedido.getValorTotal().subtract(pedido.getValorFrete()),
                pedido.getValorFrete(),
                pedido.getValorTotal(),
                pedido.getTransportadora(),
                pedido.getServicoFrete(),
                pedido.getPrazoFreteDias(),
                pedido.getCodigoRastreio(),
                pedido.getDataEntrega(),
                prazoTroca,
                pagavel ? pedido.getUrlPagamento() : null,
                pedido.getCreatedAt(),
                EnderecoDTO.from(pedido.getEndereco()),
                pedido.getItens().stream().map(PedidoItemDTO::from).toList()
        );
    }
}
