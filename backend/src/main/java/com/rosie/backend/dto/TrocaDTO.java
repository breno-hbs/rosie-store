package com.rosie.backend.dto;

import com.rosie.backend.model.FreteConta;
import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Troca;

import java.time.LocalDateTime;

// Troca vista pela cliente (POST e GET /trocas, #27): a peça, o motivo, quem paga o frete de volta e em que
// pé está, com o recado da Roseane. Como mandar a peça de volta (#63): etiquetaVolta, quando o frete é da
// Rosie, ou enderecoDevolucao, o endereço da loja, quando a troca aprovada tem o frete por conta da cliente
// (null nos outros casos, ou se o endereço da loja não estiver configurado).
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
        EtiquetaVoltaDTO etiquetaVolta,
        EnderecoDevolucaoDTO enderecoDevolucao,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    // enderecoLoja: EnderecoLoja.endereco() (pode ser null)
    public static TrocaDTO from(Troca troca, EnderecoDevolucaoDTO enderecoLoja) {
        CarrinhoProdutoDTO produto = CarrinhoProdutoDTO.from(troca.getProduto());
        boolean mandaPorContaPropria = troca.getStatus() == StatusTroca.APROVADA
                && troca.getFreteConta() == FreteConta.CLIENTE;
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
                EtiquetaVoltaDTO.from(troca),
                mandaPorContaPropria ? enderecoLoja : null,
                troca.getCreatedAt(),
                troca.getUpdatedAt()
        );
    }
}
