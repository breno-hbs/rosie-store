package com.rosie.backend.controller;

import com.rosie.backend.dto.CategoriaAdminDTO;
import com.rosie.backend.dto.CategoriaRequestDTO;
import com.rosie.backend.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Gestão de categorias pela Roseane (RF17). /admin/** já exige role ADMIN no SecurityConfig.
@RestController
@RequestMapping("/admin/categorias")
@RequiredArgsConstructor
public class AdminCategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public List<CategoriaAdminDTO> listar() {
        return categoriaService.listarAdmin();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaAdminDTO criar(@Valid @RequestBody CategoriaRequestDTO requisicao) {
        return categoriaService.criar(requisicao.nome());
    }

    @PutMapping("/{id}")
    public CategoriaAdminDTO renomear(@PathVariable Long id, @Valid @RequestBody CategoriaRequestDTO requisicao) {
        return categoriaService.renomear(id, requisicao.nome());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        categoriaService.remover(id);
    }
}
