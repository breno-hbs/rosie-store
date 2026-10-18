package com.rosie.backend.config;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Cria o admin da Roseane na subida, a partir de ROSIE_ADMIN_EMAIL e ROSIE_ADMIN_SENHA,
// só enquanto não existir nenhum ADMIN. Depois disso as variáveis são ignoradas.
@Slf4j
@Component
public class AdminInicial implements ApplicationRunner {

    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String nome;
    private final String email;
    private final String senha;

    public AdminInicial(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                        @Value("${rosie.admin.nome}") String nome,
                        @Value("${rosie.admin.email}") String email,
                        @Value("${rosie.admin.senha}") String senha) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (email.isBlank() || senha.isBlank()) {
            log.warn("Nenhum admin cadastrado. Defina ROSIE_ADMIN_EMAIL e ROSIE_ADMIN_SENHA para criar o admin inicial.");
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new IllegalStateException(
                    "ROSIE_ADMIN_SENHA precisa ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres");
        }

        String emailNormalizado = AuthService.normalizarEmail(email);
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            log.warn("ROSIE_ADMIN_EMAIL já está cadastrado como cliente; admin inicial não criado.");
            return;
        }

        usuarioRepository.save(Usuario.builder()
                .nome(nome)
                .email(emailNormalizado)
                .senhaHash(passwordEncoder.encode(senha))
                .role(Role.ADMIN)
                .build());
        log.info("Admin inicial criado: {}", emailNormalizado);
    }
}
