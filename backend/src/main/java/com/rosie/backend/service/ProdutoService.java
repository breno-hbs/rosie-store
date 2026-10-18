package com.rosie.backend.service;

import com.rosie.backend.dto.ProdutoDetalheDTO;
import com.rosie.backend.dto.ProdutoListaResponseDTO;
import com.rosie.backend.dto.ProdutoResumoDTO;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Produto;
import com.rosie.backend.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    public static final int LIMITE_MAXIMO_DESTAQUES = 50;

    private final ProdutoRepository produtoRepository;

    public ProdutoListaResponseDTO listar(Long categoriaId, int pagina, int tamanho) {
        Pageable pageable = PageRequest.of(pagina, tamanho);

        var page = (categoriaId != null)
                ? produtoRepository.findByCategoriaIdAndAtivoTrue(categoriaId, pageable)
                : produtoRepository.findByAtivoTrue(pageable);

        return ProdutoListaResponseDTO.from(page);
    }

    // limite fora de 1..LIMITE_MAXIMO_DESTAQUES é ajustado em vez de recusado
    public List<ProdutoResumoDTO> listarDestaques(int limite) {
        int limiteAjustado = Math.max(1, Math.min(limite, LIMITE_MAXIMO_DESTAQUES));

        return produtoRepository.findByAtivoTrueAndDestaqueTrueOrderByIdDesc(Limit.of(limiteAjustado)).stream()
                .map(ProdutoResumoDTO::from)
                .toList();
    }

    public ProdutoDetalheDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto com id " + id + " não encontrado"));

        return ProdutoDetalheDTO.from(produto);
    }
}