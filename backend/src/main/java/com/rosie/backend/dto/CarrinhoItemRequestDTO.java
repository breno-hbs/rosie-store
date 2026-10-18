package com.rosie.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Body de POST /carrinho/itens. O teto de 99 só evita estouro na soma; quem limita de verdade é o estoque.
public record CarrinhoItemRequestDTO(
        @NotNull(message = "Informe o produto")
        Long produtoId,

        @NotNull(message = "Escolha um tamanho")
        @Pattern(regexp = "P|M|G|GG", message = "Tamanho inválido: use P, M, G ou GG")
        String tamanho,

        @NotNull(message = "Informe a quantidade")
        @Min(value = 1, message = "A quantidade deve ser pelo menos 1")
        @Max(value = 99, message = "A quantidade deve ser no máximo 99")
        Integer quantidade
) {
}
