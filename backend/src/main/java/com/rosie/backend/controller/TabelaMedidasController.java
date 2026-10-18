package com.rosie.backend.controller;

import com.rosie.backend.dto.TabelaMedidasDTO;
import com.rosie.backend.service.TabelaMedidasService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Tabela de medidas na Página de Produto (#20). Pública, liberada no SecurityConfig.
@RestController
@RequiredArgsConstructor
public class TabelaMedidasController {

    private final TabelaMedidasService tabelaMedidasService;

    @GetMapping("/categorias/{id}/medidas")
    public TabelaMedidasDTO buscar(@PathVariable Long id) {
        return tabelaMedidasService.buscar(id);
    }
}
