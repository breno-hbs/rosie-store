package com.rosie.backend.service;

import com.rosie.backend.dto.LoginRequestDTO;
import com.rosie.backend.dto.LoginResponseDTO;
import com.rosie.backend.dto.UsuarioResumoDTO;
import com.rosie.backend.exception.CredenciaisInvalidasException;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    // Comparado quando o e-mail não existe, para a resposta levar o mesmo tempo
    // e não denunciar quais e-mails estão cadastrados
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-para-tempo-constante");
    }

    // Login do painel (RF13). E-mail inexistente, senha errada ou usuário sem role ADMIN
    // respondem igual, sem dizer qual dos dois errou.
    public LoginResponseDTO loginAdmin(LoginRequestDTO requisicao) {
        Optional<Usuario> encontrado = usuarioRepository.findByEmail(normalizarEmail(requisicao.email()));
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(requisicao.senha(), hash);

        Usuario usuario = encontrado
                .filter(u -> senhaConfere && u.getRole() == Role.ADMIN)
                .orElseThrow(CredenciaisInvalidasException::new);

        return new LoginResponseDTO(tokenService.gerar(usuario), UsuarioResumoDTO.from(usuario));
    }

    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
