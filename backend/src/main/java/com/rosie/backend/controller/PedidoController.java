package com.rosie.backend.controller;

import com.rosie.backend.dto.PedidoCriadoDTO;
import com.rosie.backend.dto.PedidoDTO;
import com.rosie.backend.dto.PedidoRequestDTO;
import com.rosie.backend.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Checkout e pedidos da cliente do token (RF10, #24). /pedidos/** já exige role CLIENTE no SecurityConfig.
// A lista de pedidos (GET /pedidos) é da Minha Conta (#26).
@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoCriadoDTO criar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PedidoRequestDTO requisicao) {
        return pedidoService.criar(clienteId(jwt), requisicao);
    }

    @GetMapping("/{id}")
    public PedidoDTO buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return pedidoService.buscar(clienteId(jwt), id);
    }

    private static Long clienteId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
