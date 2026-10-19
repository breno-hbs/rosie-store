package com.rosie.backend.service;

import com.rosie.backend.dto.ContaDTO;
import com.rosie.backend.dto.ContaRequestDTO;
import com.rosie.backend.dto.LoginRequestDTO;
import com.rosie.backend.dto.SenhaRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.CredenciaisInvalidasException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Dados pessoais da Minha Conta (#26) e direitos do titular (#31) contra o MySQL local; cada teste é desfeito no fim
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
    private EnderecoRepository enderecoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private AuthService authService;

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

    @Test
    void aceitaAVersaoEmVigorDaPolitica() {
        cliente.setPrivacidadeVersao("2020-01-01");

        ContaDTO conta = contaService.aceitarPrivacidade(cliente.getId());

        assertThat(conta.privacidadeVersao()).isEqualTo(PoliticaPrivacidade.VERSAO);
        assertThat(conta.privacidadeAceitaEm()).isNotNull();
        assertThat(PoliticaPrivacidade.aceitePendente(cliente)).isFalse();
    }

    @Test
    void exclusaoAnonimizaBloqueiaOLoginEMantemOsPedidos() {
        Endereco usado = salvarEndereco();
        Endereco naoUsado = salvarEndereco();
        Pedido entregue = salvarPedido(usado, StatusPedido.ENTREGUE);
        Pedido aPagar = salvarPedido(usado, StatusPedido.AGUARDANDO_PAGAMENTO);

        contaService.excluir(cliente.getId(), SENHA);
        entityManager.flush();
        entityManager.clear();

        Usuario excluida = usuarioRepository.findById(cliente.getId()).orElseThrow();
        assertThat(excluida.getExcluidaEm()).isNotNull();
        assertThat(excluida.getNome()).isEqualTo("Conta excluída");
        assertThat(excluida.getEmail()).doesNotContain("conta-servico").endsWith(".invalid");
        assertThat(excluida.getTelefone()).isNull();
        assertThat(passwordEncoder.matches(SENHA, excluida.getSenhaHash())).isFalse();

        // Endereço de pedido fica só com cidade e UF; o que não foi usado some
        Endereco anonimo = enderecoRepository.findById(usado.getId()).orElseThrow();
        assertThat(anonimo.getLogradouro()).isEqualTo("Endereço removido");
        assertThat(anonimo.getCep()).isEqualTo("00000000");
        assertThat(anonimo.getComplemento()).isNull();
        assertThat(anonimo.getArquivado()).isTrue();
        assertThat(anonimo.getCidade()).isEqualTo("São Paulo");
        assertThat(enderecoRepository.findById(naoUsado.getId())).isEmpty();

        // Pedidos e valores ficam (obrigação fiscal); o que não foi pago é cancelado
        Pedido mantido = pedidoRepository.findById(entregue.getId()).orElseThrow();
        assertThat(mantido.getStatus()).isEqualTo(StatusPedido.ENTREGUE);
        assertThat(mantido.getValorTotal()).isEqualByComparingTo("189.00");
        assertThat(pedidoRepository.findById(aPagar.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.CANCELADO);

        // Nem o e-mail antigo nem o novo entram
        assertThatThrownBy(() -> authService.loginCliente(new LoginRequestDTO("conta-servico@rosie.test", SENHA)))
                .isInstanceOf(CredenciaisInvalidasException.class);
        assertThatThrownBy(() -> authService.loginCliente(new LoginRequestDTO(excluida.getEmail(), SENHA)))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void senhaErradaNaoExclui() {
        assertThatThrownBy(() -> contaService.excluir(cliente.getId(), "errada-123"))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Senha incorreta");

        entityManager.flush();
        entityManager.clear();
        Usuario intacta = usuarioRepository.findById(cliente.getId()).orElseThrow();
        assertThat(intacta.getExcluidaEm()).isNull();
        assertThat(intacta.getNome()).isEqualTo("Maria Silva");
        assertThat(intacta.getEmail()).isEqualTo("conta-servico@rosie.test");
    }

    @Test
    void pedidoACaminhoSeguraAExclusao() {
        Endereco endereco = salvarEndereco();
        salvarPedido(endereco, StatusPedido.ENVIADO);

        assertThatThrownBy(() -> contaService.excluir(cliente.getId(), SENHA))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("CONTA_COM_PEDIDO_EM_ANDAMENTO");

        entityManager.flush();
        entityManager.clear();
        assertThat(usuarioRepository.findById(cliente.getId()).orElseThrow().getExcluidaEm()).isNull();
        assertThat(enderecoRepository.findById(endereco.getId()).orElseThrow().getLogradouro())
                .isEqualTo("Rua das Flores");
    }

    private Endereco salvarEndereco() {
        return enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120").complemento("Apto 14")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
    }

    private Pedido salvarPedido(Endereco endereco, StatusPedido status) {
        return pedidoRepository.save(Pedido.builder()
                .usuario(cliente).endereco(endereco).status(status).valorTotal(new BigDecimal("189.00")).build());
    }
}
