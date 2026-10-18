package com.rosie.backend.controller;

import com.rosie.backend.dto.EnderecoDTO;
import com.rosie.backend.dto.EnderecoRequestDTO;
import com.rosie.backend.service.EnderecoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

// Endereços de entrega da cliente do token (#24). /enderecos/** exige role CLIENTE no SecurityConfig.
@RestController
@RequestMapping("/enderecos")
@RequiredArgsConstructor
public class EnderecoController {

    private final EnderecoService enderecoService;

    @GetMapping
    public List<EnderecoDTO> listar(@AuthenticationPrincipal Jwt jwt) {
        return enderecoService.listar(clienteId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnderecoDTO criar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EnderecoRequestDTO requisicao) {
        return enderecoService.criar(clienteId(jwt), requisicao);
    }

    @PutMapping("/{id}")
    public EnderecoDTO atualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                 @Valid @RequestBody EnderecoRequestDTO requisicao) {
        return enderecoService.atualizar(clienteId(jwt), id, requisicao);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        enderecoService.remover(clienteId(jwt), id);
    }

    private static Long clienteId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
