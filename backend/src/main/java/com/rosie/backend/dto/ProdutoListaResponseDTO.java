package com.rosie.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProdutoListaResponseDTO(
        List<ProdutoResumoDTO> conteudo,
        int totalPaginas,
        long totalItens
) {

    public static ProdutoListaResponseDTO from(Page<com.rosie.backend.model.Produto> page) {
        List<ProdutoResumoDTO> conteudo = page.getContent().stream()
                .map(ProdutoResumoDTO::from)
                .toList();

        return new ProdutoListaResponseDTO(conteudo, page.getTotalPages(), page.getTotalElements());
    }
}
