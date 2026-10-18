package com.rosie.backend.service;

import com.rosie.backend.dto.ContaDTO;
import com.rosie.backend.dto.ContaRequestDTO;
import com.rosie.backend.dto.SenhaRequestDTO;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

// Dados pessoais da Minha Conta (#26): nome, telefone e troca de senha da cliente do token.
// O e-mail não muda por aqui (é o login; trocar exigiria confirmar o novo endereço).
@Service
@RequiredArgsConstructor
@Transactional
public class ContaService {

    // O BCrypt só aceita até 72 bytes; acentos ocupam 2, então o @Size do DTO não basta (como no cadastro)
    private static final int BYTES_MAXIMOS_SENHA = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public ContaDTO buscar(Long clienteId) {
        return ContaDTO.from(cliente(clienteId));
    }

    public ContaDTO atualizar(Long clienteId, ContaRequestDTO requisicao) {
        Usuario usuario = cliente(clienteId);
        usuario.setNome(requisicao.nome().trim());
        usuario.setTelefone(requisicao.telefone());
        return ContaDTO.from(usuario);
    }

    // Senha atual errada é 400 (não 401): a sessão continua valendo, só o formulário está errado.
    // Tokens já emitidos seguem válidos até vencer (JWT sem estado, 2 horas).
    public void trocarSenha(Long clienteId, SenhaRequestDTO requisicao) {
        if (requisicao.novaSenha().getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_SENHA) {
            throw new DadosInvalidosException("A senha precisa ter de 8 a 72 caracteres");
        }
        Usuario usuario = cliente(clienteId);
        if (!passwordEncoder.matches(requisicao.senhaAtual(), usuario.getSenhaHash())) {
            throw new DadosInvalidosException("Senha atual incorreta");
        }
        usuario.setSenhaHash(passwordEncoder.encode(requisicao.novaSenha()));
    }

    private Usuario cliente(Long clienteId) {
        return usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }
}
