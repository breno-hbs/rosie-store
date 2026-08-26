package com.rosie.backend.controller;

import com.rosie.backend.dto.ProdutoDetalheDTO;
import com.rosie.backend.dto.ProdutoListaResponseDTO;
import com.rosie.backend.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @GetMapping("/produtos")
    public ProdutoListaResponseDTO listar(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamanho
    ) {
        return produtoService.listar(categoriaId, pagina, tamanho);
    }

    @GetMapping("/produtos/{id}")
    public ProdutoDetalheDTO buscarPorId(@PathVariable Long id) {
        return produtoService.buscarPorId(id);
    }
}