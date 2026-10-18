package com.rosie.backend.controller;

import com.rosie.backend.dto.FreteRequestDTO;
import com.rosie.backend.dto.FreteResponseDTO;
import com.rosie.backend.service.FreteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Cálculo de frete sobre o carrinho da cliente do token (RF11, #23). /frete/** exige role CLIENTE no SecurityConfig.
@RestController
@RequestMapping("/frete")
@RequiredArgsConstructor
public class FreteController {

    private final FreteService freteService;

    @PostMapping("/calcular")
    public FreteResponseDTO calcular(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FreteRequestDTO requisicao) {
        return freteService.calcular(Long.valueOf(jwt.getSubject()), requisicao.cep());
    }
}
