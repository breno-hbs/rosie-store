package com.rosie.backend.dto;

import com.rosie.backend.model.MotivoTroca;
import com.rosie.backend.model.Tamanho;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Body de POST /trocas (#27). tamanho só é obrigatório se o produto veio em mais de um tamanho no pedido;
// sem quantidade, volta tudo o que foi comprado da peça. Não há campo de frete: freteConta sai do motivo.
public record TrocaRequestDTO(
        @NotNull(message = "Informe o pedido")
        Long pedidoId,

        @NotNull(message = "Informe a peça")
        Long produtoId,

        Tamanho tamanho,

        @Positive(message = "A quantidade deve ser maior que zero")
        Integer quantidade,

        @NotNull(message = "Informe o motivo da troca")
        MotivoTroca motivo,

        @Size(max = 1000, message = "A descrição pode ter até 1000 caracteres")
        String observacao
) {
}
