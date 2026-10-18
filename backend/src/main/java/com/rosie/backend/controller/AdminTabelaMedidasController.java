package com.rosie.backend.controller;

import com.rosie.backend.dto.TabelaMedidasDTO;
import com.rosie.backend.dto.TabelaMedidasRequestDTO;
import com.rosie.backend.service.TabelaMedidasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Tabela de medidas de cada categoria, editada pela Roseane (#20). /admin/** já exige role ADMIN.
@RestController
@RequestMapping("/admin/categorias/{id}/medidas")
@RequiredArgsConstructor
public class AdminTabelaMedidasController {

    private final TabelaMedidasService tabelaMedidasService;

    @GetMapping
    public TabelaMedidasDTO buscar(@PathVariable Long id) {
        return tabelaMedidasService.buscar(id);
    }

    @PutMapping
    public TabelaMedidasDTO salvar(@PathVariable Long id, @Valid @RequestBody TabelaMedidasRequestDTO requisicao) {
        return tabelaMedidasService.salvar(id, requisicao.medidas());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        tabelaMedidasService.remover(id);
    }
}
