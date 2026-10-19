package com.rosie.backend.dto;

import com.rosie.backend.model.Produto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

// Peso e medidas de uma peça já embalada (#56), usados na cotação do frete e na etiqueta. Todos opcionais:
// o que ficar nulo usa o pacote padrão (rosie.frete.peca.*). Limites dos Correios: até 30 kg e 100 cm por lado.
public record PacoteDTO(
        @DecimalMin(value = "0", inclusive = false, message = "O peso deve ser maior que zero")
        @DecimalMax(value = "30", message = "O peso pode ser de até 30 kg")
        @Digits(integer = 2, fraction = 3, message = "Peso inválido: use até 3 casas decimais")
        BigDecimal pesoKg,

        @Min(value = 1, message = "A altura da embalagem deve ser de pelo menos 1 cm")
        @Max(value = 100, message = "A altura da embalagem pode ser de até 100 cm")
        Integer alturaCm,

        @Min(value = 1, message = "A largura da embalagem deve ser de pelo menos 1 cm")
        @Max(value = 100, message = "A largura da embalagem pode ser de até 100 cm")
        Integer larguraCm,

        @Min(value = 1, message = "O comprimento da embalagem deve ser de pelo menos 1 cm")
        @Max(value = 100, message = "O comprimento da embalagem pode ser de até 100 cm")
        Integer comprimentoCm
) {

    public static PacoteDTO from(Produto produto) {
        return new PacoteDTO(produto.getPesoKg(), produto.getAlturaCm(), produto.getLarguraCm(),
                produto.getComprimentoCm());
    }
}
