package com.rosie.backend.controller;

import com.rosie.backend.dto.ContaDTO;
import com.rosie.backend.dto.ContaRequestDTO;
import com.rosie.backend.dto.SenhaRequestDTO;
import com.rosie.backend.service.ContaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Dados pessoais da cliente do token, na Minha Conta (#26). /conta/** exige role CLIENTE no SecurityConfig.
@RestController
@RequestMapping("/conta")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;

    @GetMapping
    public ContaDTO buscar(@AuthenticationPrincipal Jwt jwt) {
        return contaService.buscar(clienteId(jwt));
    }

    @PutMapping
    public ContaDTO atualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ContaRequestDTO requisicao) {
        return contaService.atualizar(clienteId(jwt), requisicao);
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SenhaRequestDTO requisicao) {
        contaService.trocarSenha(clienteId(jwt), requisicao);
    }

    private static Long clienteId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
