package com.rosie.backend.controller;

import com.rosie.backend.dto.CarrinhoDTO;
import com.rosie.backend.dto.CarrinhoItemRequestDTO;
import com.rosie.backend.dto.QuantidadeRequestDTO;
import com.rosie.backend.service.CarrinhoService;
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

// Carrinho da cliente do token (RF09, #22). /carrinho/** já exige role CLIENTE no SecurityConfig.
// Toda rota responde com o carrinho atualizado.
@RestController
@RequestMapping("/carrinho")
@RequiredArgsConstructor
public class CarrinhoController {

    private final CarrinhoService carrinhoService;

    @GetMapping
    public CarrinhoDTO buscar(@AuthenticationPrincipal Jwt jwt) {
        return carrinhoService.buscar(clienteId(jwt));
    }

    @PostMapping("/itens")
    @ResponseStatus(HttpStatus.CREATED)
    public CarrinhoDTO adicionar(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody CarrinhoItemRequestDTO requisicao) {
        return carrinhoService.adicionar(clienteId(jwt), requisicao);
    }

    @PutMapping("/itens/{itemId}")
    public CarrinhoDTO atualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId,
                                 @Valid @RequestBody QuantidadeRequestDTO requisicao) {
        return carrinhoService.atualizarQuantidade(clienteId(jwt), itemId, requisicao.quantidade());
    }

    @DeleteMapping("/itens/{itemId}")
    public CarrinhoDTO remover(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId) {
        return carrinhoService.remover(clienteId(jwt), itemId);
    }

    private static Long clienteId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
