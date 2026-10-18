package com.rosie.backend.dto;

import com.rosie.backend.model.MedidaCategoria;
import com.rosie.backend.model.Tamanho;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

// Uma linha da tabela de medidas: nome e o valor de cada tamanho (texto curto, ex.: "84–88").
// Na resposta, `valores` sempre traz P, M, G e GG, nessa ordem, com "" quando vazio.
// No body, tamanho ausente ou null conta como vazio.
public record MedidaDTO(
        @NotBlank(message = "Informe o nome da medida")
        @Size(max = 40, message = "O nome da medida pode ter no máximo 40 caracteres")
        String nome,

        Map<
                @NotNull(message = "Tamanho inválido: use P, M, G ou GG")
                @Pattern(regexp = "P|M|G|GG", message = "Tamanho inválido: use P, M, G ou GG") String,
                @Size(max = 20, message = "Cada valor da tabela pode ter no máximo 20 caracteres") String
        > valores
) {

    public static MedidaDTO from(MedidaCategoria medida) {
        Map<String, String> valores = new LinkedHashMap<>();
        for (Tamanho tamanho : Tamanho.values()) {
            String valor = medida.getValor(tamanho);
            valores.put(tamanho.name(), valor == null ? "" : valor);
        }
        return new MedidaDTO(medida.getNome(), valores);
    }
}
