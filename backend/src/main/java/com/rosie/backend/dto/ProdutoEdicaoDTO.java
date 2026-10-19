package com.rosie.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Body de PUT /admin/produtos/{id}. Não tem estoque: ele só muda por PUT /admin/produtos/{id}/estoque,
// e um "estoque" no corpo é ignorado. destaque, ativo e pacote ausentes mantêm o valor atual do produto.
public record ProdutoEdicaoDTO(
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

        // Ausente (null) mantém o peso e as medidas atuais; presente substitui os quatro, e campo nulo volta
        // para o pacote padrão (#56)
        @Valid
        PacoteDTO pacote
) {
}
