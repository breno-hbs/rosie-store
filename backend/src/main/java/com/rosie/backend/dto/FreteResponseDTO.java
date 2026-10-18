package com.rosie.backend.dto;

import java.util.List;

// cep volta só com os 8 dígitos; opcoes vem do mais barato para o mais caro (lista vazia: nenhuma
// transportadora atende o CEP)
public record FreteResponseDTO(String cep, List<OpcaoFreteDTO> opcoes) {
}
