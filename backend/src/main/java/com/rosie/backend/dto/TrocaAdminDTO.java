package com.rosie.backend.dto;

import com.rosie.backend.model.Troca;

import java.time.LocalDateTime;

// Troca no painel (GET /admin/trocas, #27): o mesmo que a cliente vê, mais quem pediu e a etiqueta de volta
// com o que só o painel precisa (#63)
public record TrocaAdminDTO(
        Long id,
        Long pedidoId,
        ClientePedidoDTO cliente,
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
        EtiquetaVoltaAdminDTO etiquetaVolta,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TrocaAdminDTO from(Troca troca) {
        TrocaDTO dados = TrocaDTO.from(troca, null);
        return new TrocaAdminDTO(
                dados.id(),
                dados.pedidoId(),
                ClientePedidoDTO.from(troca.getPedido().getUsuario()),
                dados.produtoId(),
                dados.nome(),
                dados.imagemPrincipal(),
                dados.tamanho(),
                dados.quantidade(),
                dados.motivo(),
                dados.freteConta(),
                dados.status(),
                dados.observacao(),
                dados.resposta(),
                EtiquetaVoltaAdminDTO.from(troca),
                dados.createdAt(),
                dados.updatedAt()
        );
    }
}
