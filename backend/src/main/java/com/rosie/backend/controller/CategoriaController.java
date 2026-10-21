package com.rosie.backend.controller;

import com.rosie.backend.dto.CategoriaResumoDTO;
import com.rosie.backend.service.CategoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping("/categorias")
    public List<CategoriaResumoDTO> listar() {
        return categoriaService.listar();
    }

    // Página da categoria na loja (#88): aceita também o slug de antes de renomear e responde com o atual
    @GetMapping("/categorias/{slug}")
    public CategoriaResumoDTO buscarPorSlug(@PathVariable String slug) {
        return categoriaService.buscarPorSlug(slug);
    }
}
