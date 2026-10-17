package com.rosie.backend.service;

import com.rosie.backend.dto.ProdutoDetalheDTO;
import com.rosie.backend.dto.ProdutoListaResponseDTO;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Produto;
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
                ? produtoRepository.findByCategoriaIdAndAtivoTrue(categoriaId, pageable)
                : produtoRepository.findByAtivoTrue(pageable);

        return ProdutoListaResponseDTO.from(page);
    }

    public ProdutoDetalheDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto com id " + id + " não encontrado"));

        return ProdutoDetalheDTO.from(produto);
    }
}