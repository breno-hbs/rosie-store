package com.rosie.backend.controller;

import com.rosie.backend.dto.PoliticaPrivacidadeDTO;
import com.rosie.backend.service.PoliticaPrivacidade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Versão da política de privacidade em vigor (#31), pública: a página da política mostra a mesma versão
// que o cadastro grava, sem repetir o número no front
@RestController
@RequestMapping("/privacidade")
public class PrivacidadeController {

    @GetMapping
    public PoliticaPrivacidadeDTO emVigor() {
        return new PoliticaPrivacidadeDTO(PoliticaPrivacidade.VERSAO, PoliticaPrivacidade.VIGENCIA);
    }
}
