package com.rosie.backend.dto;

import com.rosie.backend.model.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Detalhe do pedido no painel (GET /admin/pedidos/{id}, #25): o que a cliente vê no PedidoDTO, mais quem
// comprou, o pagamento e a etiqueta do Melhor Envio
public record PedidoAdminDTO(
        Long id,
        String status,
        ClientePedidoDTO cliente,
        String formaPagamento,
        String pagamentoId,
        LocalDateTime pagoEm,
        String pendencia,
        BigDecimal subtotal,
        BigDecimal valorFrete,
        BigDecimal valorTotal,
        String transportadora,
        String servicoFrete,
        Integer prazoFreteDias,
        String envioId,
        String urlEtiqueta,
        LocalDateTime etiquetaGeradaEm,
        String codigoRastreio,
        LocalDateTime createdAt,
        EnderecoDTO endereco,
        List<PedidoItemDTO> itens
) {

    public static PedidoAdminDTO from(Pedido pedido) {
        return new PedidoAdminDTO(
                pedido.getId(),
                pedido.getStatus().name(),
                ClientePedidoDTO.from(pedido.getUsuario()),
                pedido.getFormaPagamento() == null ? null : pedido.getFormaPagamento().name(),
                pedido.getPagamentoId(),
                pedido.getPagoEm(),
                pedido.getPendencia(),
                pedido.getValorTotal().subtract(pedido.getValorFrete()),
                pedido.getValorFrete(),
                pedido.getValorTotal(),
                pedido.getTransportadora(),
                pedido.getServicoFrete(),
                pedido.getPrazoFreteDias(),
                pedido.getEnvioId(),
                pedido.getUrlEtiqueta(),
                pedido.getEtiquetaGeradaEm(),
                pedido.getCodigoRastreio(),
                pedido.getCreatedAt(),
                EnderecoDTO.from(pedido.getEndereco()),
                pedido.getItens().stream().map(PedidoItemDTO::from).toList()
        );
    }
}
