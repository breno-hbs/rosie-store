package com.rosie.backend.controller;

import com.rosie.backend.dto.ProdutoDetalheDTO;
import com.rosie.backend.dto.ProdutoListaResponseDTO;
import com.rosie.backend.dto.ProdutoResumoDTO;
import com.rosie.backend.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @GetMapping("/produtos")
    public ProdutoListaResponseDTO listar(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamanho
    ) {
        return produtoService.listar(categoriaId, busca, pagina, tamanho);
    }

    @GetMapping("/produtos/destaques")
    public List<ProdutoResumoDTO> listarDestaques(@RequestParam(defaultValue = "12") int limite) {
        return produtoService.listarDestaques(limite);
    }

    @GetMapping("/produtos/{id}")
    public ProdutoDetalheDTO buscarPorId(@PathVariable Long id) {
        return produtoService.buscarPorId(id);
    }
}