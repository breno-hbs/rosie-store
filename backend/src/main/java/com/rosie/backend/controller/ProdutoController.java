package com.rosie.backend.controller;

import com.rosie.backend.dto.ProdutoListaResponseDTO;
import com.rosie.backend.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    // GET /produtos?categoriaId=1&pagina=0&tamanho=12  (Contrato da API)
    @GetMapping("/produtos")
    public ProdutoListaResponseDTO listar(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamanho
    ) {
        return produtoService.listar(categoriaId, pagina, tamanho);
    }
}
