package com.rosie.backend.controller;

import com.rosie.backend.dto.TrocaAdminDTO;
import com.rosie.backend.dto.TrocaStatusRequestDTO;
import com.rosie.backend.service.EtiquetaService;
import com.rosie.backend.service.TrocaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Trocas no painel (#27): a Roseane acompanha, muda o status e gera a etiqueta de volta (#63). /admin/** já exige role ADMIN no SecurityConfig.
@RestController
@RequestMapping("/admin/trocas")
@RequiredArgsConstructor
public class AdminTrocaController {

    private final TrocaService trocaService;
    private final EtiquetaService etiquetaService;

    @GetMapping
    public List<TrocaAdminDTO> listar() {
        return trocaService.listarTodas();
    }

    @PutMapping("/{id}/status")
    public TrocaAdminDTO atualizarStatus(@PathVariable Long id, @Valid @RequestBody TrocaStatusRequestDTO requisicao) {
        return trocaService.atualizarStatus(id, requisicao);
    }

    // Compra e gera a etiqueta de volta no Melhor Envio (logística reversa, #63); repetir não compra outra
    @PostMapping("/{id}/etiqueta-volta")
    public TrocaAdminDTO gerarEtiquetaVolta(@PathVariable Long id) {
        return etiquetaService.gerarVolta(id);
    }
}
