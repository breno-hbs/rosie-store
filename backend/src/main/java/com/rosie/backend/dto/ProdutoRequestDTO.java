package com.rosie.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

// Body de POST e PUT /admin/produtos. destaque e ativo são opcionais: ausentes, o POST usa
// destaque = false e ativo = true, e o PUT mantém o valor atual do produto.
public record ProdutoRequestDTO(
        @NotBlank(message = "Informe o nome do produto")
        @Size(max = 150, message = "O nome pode ter até 150 caracteres")
        String nome,

        String descricao,

        String composicao,

        @NotNull(message = "Informe o preço")
        @DecimalMin(value = "0", inclusive = false, message = "O preço deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "Preço inválido: use até 2 casas decimais")
        BigDecimal preco,

        @NotNull(message = "Selecione a categoria")
        Long categoriaId,

        Boolean destaque,

        Boolean ativo,

        // Tamanhos que o produto tem; os que ficarem de fora não são vendidos
        @Valid
        List<@NotNull(message = "Item de estoque vazio") @Valid EstoqueDTO> estoque
) {
}
