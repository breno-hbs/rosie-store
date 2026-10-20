package com.rosie.backend.controller;

import com.rosie.backend.dto.AceitePrivacidadeRequestDTO;
import com.rosie.backend.dto.ConfirmacaoEmailRequestDTO;
import com.rosie.backend.dto.ContaDTO;
import com.rosie.backend.dto.ContaRequestDTO;
import com.rosie.backend.dto.EmailConfirmadoDTO;
import com.rosie.backend.dto.EmailPendenteDTO;
import com.rosie.backend.dto.EmailRequestDTO;
import com.rosie.backend.dto.ExclusaoContaRequestDTO;
import com.rosie.backend.dto.SenhaRequestDTO;
import com.rosie.backend.service.ContaService;
import com.rosie.backend.service.MudancaEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Dados pessoais da cliente do token, na Minha Conta (#26), a troca de e-mail (#61) e os direitos do titular (#31).
// /conta/** exige role CLIENTE no SecurityConfig, menos a confirmação do e-mail novo (pública, pelo token do link).
@RestController
@RequestMapping("/conta")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;
    private final MudancaEmailService mudancaEmailService;

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

    // Troca de e-mail (#61): 202 porque o e-mail novo só vale depois da confirmação pelo link mandado a ele
    @PutMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailPendenteDTO solicitarTrocaEmail(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody EmailRequestDTO requisicao) {
        return mudancaEmailService.solicitar(clienteId(jwt), requisicao);
    }

    // Volta do link mandado ao e-mail novo: sem token de sessão (o link pode ser aberto em outro aparelho)
    @PostMapping("/email/confirmar")
    public EmailConfirmadoDTO confirmarEmail(@RequestBody ConfirmacaoEmailRequestDTO requisicao) {
        return mudancaEmailService.confirmar(requisicao.token());
    }

    // Aceite da versão em vigor da política de privacidade (#31), pedido no login ou no checkout
    @PostMapping("/privacidade")
    public ContaDTO aceitarPrivacidade(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody AceitePrivacidadeRequestDTO requisicao) {
        return contaService.aceitarPrivacidade(clienteId(jwt));
    }

    // Exclusão da conta (LGPD, #31): POST com a senha no corpo (DELETE com corpo nem sempre passa por proxies)
    @PostMapping("/exclusao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExclusaoContaRequestDTO requisicao) {
        contaService.excluir(clienteId(jwt), requisicao.senha());
    }

    private static Long clienteId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
