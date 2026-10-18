package com.rosie.backend.controller;

import com.rosie.backend.dto.LoginRequestDTO;
import com.rosie.backend.dto.LoginResponseDTO;
import com.rosie.backend.dto.UsuarioResumoDTO;
import com.rosie.backend.model.Role;
import com.rosie.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO requisicao) {
        return authService.loginAdmin(requisicao);
    }

    // Dono do token: o painel usa para conferir se a sessão ainda vale
    @GetMapping("/me")
    public UsuarioResumoDTO me(@AuthenticationPrincipal Jwt jwt) {
        return new UsuarioResumoDTO(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("nome"),
                Role.valueOf(jwt.getClaimAsString("role")));
    }
}
