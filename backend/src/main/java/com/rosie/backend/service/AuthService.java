package com.rosie.backend.service;

import com.rosie.backend.dto.LoginRequestDTO;
import com.rosie.backend.dto.LoginResponseDTO;
import com.rosie.backend.dto.RegistroRequestDTO;
import com.rosie.backend.dto.UsuarioResumoDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.CredenciaisInvalidasException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private static final String MENSAGEM_EMAIL_DUPLICADO = "Este e-mail já está cadastrado. Entre com ele ou use outro.";

    // O BCrypt só aceita até 72 bytes; acentos ocupam 2, então o @Size do DTO não basta
    private static final int BYTES_MAXIMOS_SENHA = 72;

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

    // Cadastro de cliente (RF08): já devolve o token, para a pessoa sair do cadastro logada.
    // Guarda o aceite da política de privacidade (RNF06) com a data e a versão em vigor.
    @Transactional
    public LoginResponseDTO registrar(RegistroRequestDTO requisicao) {
        if (requisicao.senha().getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_SENHA) {
            throw new DadosInvalidosException("A senha precisa ter de 8 a 72 caracteres");
        }

        String email = normalizarEmail(requisicao.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflitoException(MENSAGEM_EMAIL_DUPLICADO, "EMAIL_JA_CADASTRADO");
        }

        Usuario usuario = Usuario.builder()
                .nome(requisicao.nome().trim())
                .email(email)
                .senhaHash(passwordEncoder.encode(requisicao.senha()))
                .telefone(requisicao.telefone())
                .role(Role.CLIENTE)
                .privacidadeAceitaEm(LocalDateTime.now())
                .privacidadeVersao(PoliticaPrivacidade.VERSAO)
                .build();
        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Dois cadastros com o mesmo e-mail ao mesmo tempo: o índice único segura o segundo
            throw new ConflitoException(MENSAGEM_EMAIL_DUPLICADO, "EMAIL_JA_CADASTRADO");
        }

        return resposta(usuario);
    }

    // Login da loja (RF08). Só entra CLIENTE: a Roseane entra pelo painel (/admin/auth/login),
    // e cada login emite o token da sua área.
    public LoginResponseDTO loginCliente(LoginRequestDTO requisicao) {
        return login(requisicao, Role.CLIENTE);
    }

    // Login do painel (RF13)
    public LoginResponseDTO loginAdmin(LoginRequestDTO requisicao) {
        return login(requisicao, Role.ADMIN);
    }

    // E-mail inexistente, senha errada, usuário de outra role ou conta excluída (#31) respondem igual,
    // sem dizer qual dos dois errou.
    private LoginResponseDTO login(LoginRequestDTO requisicao, Role roleExigida) {
        Optional<Usuario> encontrado = usuarioRepository.findByEmail(normalizarEmail(requisicao.email()));
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio);
        boolean senhaConfere = passwordEncoder.matches(requisicao.senha(), hash);

        Usuario usuario = encontrado
                .filter(u -> senhaConfere && u.getRole() == roleExigida && u.getExcluidaEm() == null)
                .orElseThrow(CredenciaisInvalidasException::new);

        return resposta(usuario);
    }

    // Quem aceitou uma versão antiga da política de privacidade entra normalmente, mas a loja pede o aceite
    // da nova antes de seguir (#31); o checkout também exige
    private LoginResponseDTO resposta(Usuario usuario) {
        return new LoginResponseDTO(tokenService.gerar(usuario), UsuarioResumoDTO.from(usuario),
                PoliticaPrivacidade.aceitePendente(usuario));
    }

    public static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
