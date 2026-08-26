package com.rosie.backend.service;

import com.rosie.backend.dto.ProdutoListaResponseDTO;
import com.rosie.backend.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoListaResponseDTO listar(Long categoriaId, int pagina, int tamanho) {
        Pageable pageable = PageRequest.of(pagina, tamanho);

        var page = (categoriaId != null)
                ? produtoRepository.findByCategoriaId(categoriaId, pageable)
                : produtoRepository.findByAtivoTrue(pageable);

        return ProdutoListaResponseDTO.from(page);
    }
}
