package com.rosie.backend.controller;

import com.rosie.backend.dto.TrocaDTO;
import com.rosie.backend.dto.TrocaRequestDTO;
import com.rosie.backend.service.TrocaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Troca e devolução da cliente do token (RF18, RNF08, #27). /trocas/** já exige role CLIENTE no SecurityConfig.
@RestController
@RequestMapping("/trocas")
@RequiredArgsConstructor
public class TrocaController {

    private final TrocaService trocaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrocaDTO solicitar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrocaRequestDTO requisicao) {
        return trocaService.solicitar(clienteId(jwt), requisicao);
    }

    @GetMapping
    public List<TrocaDTO> listar(@AuthenticationPrincipal Jwt jwt) {
        return trocaService.listar(clienteId(jwt));
    }

    private static Long clienteId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
