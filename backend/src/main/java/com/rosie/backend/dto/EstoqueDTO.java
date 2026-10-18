package com.rosie.backend.dto;

import com.rosie.backend.model.ProdutoEstoque;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Resposta do detalhe do produto e item de estoque no cadastro do painel (as validações só valem no body)
public record EstoqueDTO(
        @NotNull(message = "Informe o tamanho")
        @Pattern(regexp = "P|M|G|GG", message = "Tamanho inválido: use P, M, G ou GG")
        String tamanho,

        @NotNull(message = "Informe a quantidade do tamanho")
        @Min(value = 0, message = "A quantidade não pode ser negativa")
        Integer quantidade
) {

    public static EstoqueDTO from(ProdutoEstoque estoque) {
        return new EstoqueDTO(estoque.getTamanho().name(), estoque.getQuantidade());
    }
}
