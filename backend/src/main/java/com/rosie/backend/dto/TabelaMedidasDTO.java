package com.rosie.backend.dto;

import java.util.List;

// Tabela de medidas de uma categoria (Página de Produto e painel). `medidas` vazia = categoria sem tabela.
public record TabelaMedidasDTO(
        Long categoriaId,
        String categoria,
        String unidade,
        List<MedidaDTO> medidas
) {
}
