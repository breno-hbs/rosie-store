package com.rosie.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Body de POST /pedidos (#24). Só escolhas: itens, preços e frete o servidor calcula de novo.
// servicoFreteId é o "id" de uma opção do POST /frete/calcular.
public record PedidoRequestDTO(
        @NotNull(message = "Escolha o endereço de entrega")
        Long enderecoId,

        @NotNull(message = "Escolha a forma de entrega")
        Integer servicoFreteId,

        @NotNull(message = "Escolha a forma de pagamento")
        @Pattern(regexp = "CARTAO|PIX", message = "Forma de pagamento inválida: use CARTAO ou PIX")
        String formaPagamento
) {
}
