package com.rosie.backend.controller;

import com.rosie.backend.dto.EsqueciSenhaRequestDTO;
import com.rosie.backend.dto.LoginRequestDTO;
import com.rosie.backend.dto.LoginResponseDTO;
import com.rosie.backend.dto.MensagemDTO;
import com.rosie.backend.dto.RedefinicaoSenhaRequestDTO;
import com.rosie.backend.dto.RegistroRequestDTO;
import com.rosie.backend.dto.UsuarioResumoDTO;
import com.rosie.backend.model.Role;
import com.rosie.backend.service.AuthService;
import com.rosie.backend.service.RedefinicaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
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

// Conta do cliente da loja (RF08). O login do painel fica em AdminAuthController; o "esqueci minha senha" (#93)
// fica aqui e serve aos dois.
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String MENSAGEM_LINK_ENVIADO = "Se houver uma conta com esse e-mail, enviamos um link para criar "
            + "uma nova senha.";

    private final AuthService authService;
    private final RedefinicaoSenhaService redefinicaoSenhaService;

    @PostMapping("/registrar")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponseDTO registrar(@Valid @RequestBody RegistroRequestDTO requisicao) {
        return authService.registrar(requisicao);
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO requisicao) {
        return authService.loginCliente(requisicao);
    }

    // Esqueci minha senha (#93), público e o mesmo para a cliente e o admin: sempre 202 com o mesmo texto, exista
    // ou não a conta. O IP é o do cliente: no perfil prod, o server.forward-headers-strategy lê o X-Forwarded-For
    // do proxy do Railway
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensagemDTO esqueciSenha(@Valid @RequestBody EsqueciSenhaRequestDTO requisicao,
                                    HttpServletRequest request) {
        redefinicaoSenhaService.solicitar(requisicao.email(), request.getRemoteAddr());
        return new MensagemDTO(MENSAGEM_LINK_ENVIADO);
    }

    // Nova senha pelo link (#93), público: o token do link é a prova
    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@Valid @RequestBody RedefinicaoSenhaRequestDTO requisicao) {
        redefinicaoSenhaService.redefinir(requisicao.token(), requisicao.novaSenha());
    }

    // Dono do token de cliente: a Nav usa para conferir se a sessão ainda vale
    @GetMapping("/me")
    public UsuarioResumoDTO me(@AuthenticationPrincipal Jwt jwt) {
        return new UsuarioResumoDTO(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("nome"),
                Role.valueOf(jwt.getClaimAsString("role")));
    }
}
