package com.rosie.backend.service;

import com.rosie.backend.dto.ContaDTO;
import com.rosie.backend.dto.ContaRequestDTO;
import com.rosie.backend.dto.SenhaRequestDTO;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Dados pessoais da Minha Conta (#26) contra o MySQL local; cada teste é desfeito no fim
@SpringBootTest
@Transactional
class ContaServiceTests {

    private static final String SENHA = "senha-da-cliente-123";

    @Autowired
    private ContaService contaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private Usuario cliente;

    @BeforeEach
    void setUp() {
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Silva")
                .email("conta-servico@rosie.test")
                .senhaHash(passwordEncoder.encode(SENHA))
                .telefone("11987654321")
                .role(Role.CLIENTE)
                .build());
    }

    @Test
    void buscaOsDadosDaCliente() {
        ContaDTO conta = contaService.buscar(cliente.getId());

        assertThat(conta.id()).isEqualTo(cliente.getId());
        assertThat(conta.nome()).isEqualTo("Maria Silva");
        assertThat(conta.email()).isEqualTo("conta-servico@rosie.test");
        assertThat(conta.telefone()).isEqualTo("11987654321");
    }

    @Test
    void atualizaNomeETelefoneSemMexerNoEmail() {
        ContaDTO conta = contaService.atualizar(cliente.getId(), new ContaRequestDTO("  Maria Souza  ", "1134567890"));

        assertThat(conta.nome()).isEqualTo("Maria Souza");
        assertThat(conta.telefone()).isEqualTo("1134567890");

        entityManager.flush();
        entityManager.clear();
        Usuario salvo = usuarioRepository.findById(cliente.getId()).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Maria Souza");
        assertThat(salvo.getTelefone()).isEqualTo("1134567890");
        assertThat(salvo.getEmail()).isEqualTo("conta-servico@rosie.test");
    }

    @Test
    void trocaASenhaComASenhaAtualCerta() {
        contaService.trocarSenha(cliente.getId(), new SenhaRequestDTO(SENHA, "nova-senha-456"));

        entityManager.flush();
        entityManager.clear();
        String hash = usuarioRepository.findById(cliente.getId()).orElseThrow().getSenhaHash();
        assertThat(passwordEncoder.matches("nova-senha-456", hash)).isTrue();
        assertThat(passwordEncoder.matches(SENHA, hash)).isFalse();
    }

    @Test
    void senhaAtualErradaNaoTrocaNada() {
        assertThatThrownBy(() -> contaService.trocarSenha(cliente.getId(), new SenhaRequestDTO("errada-123", "nova-senha-456")))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Senha atual incorreta");

        String hash = usuarioRepository.findById(cliente.getId()).orElseThrow().getSenhaHash();
        assertThat(passwordEncoder.matches(SENHA, hash)).isTrue();
    }

    @Test
    void novaSenhaComMaisDe72BytesERecusada() {
        // 40 caracteres acentuados = 80 bytes: passa no @Size, mas o BCrypt cortaria
        String longa = "á".repeat(40);
        assertThatThrownBy(() -> contaService.trocarSenha(cliente.getId(), new SenhaRequestDTO(SENHA, longa)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("A senha precisa ter de 8 a 72 caracteres");
    }
}
