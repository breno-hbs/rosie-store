package com.rosie.backend.service;

import com.rosie.backend.dto.LoginRequestDTO;
import com.rosie.backend.dto.SenhaRequestDTO;
import com.rosie.backend.exception.CredenciaisInvalidasException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.MuitasTentativasException;
import com.rosie.backend.exception.TokenInvalidoException;
import com.rosie.backend.model.RedefinicaoSenha;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.RedefinicaoSenhaRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.EnviadorEmail.Email;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Esqueci minha senha (#93) contra o MySQL, sem a transação do teste: o link e o aviso só saem depois do commit,
// pela FilaEmail. O teste tira o token do link do EnviadorEmailFalso. Apaga tudo no fim.
@SpringBootTest
@Import(EnviadorEmailFalso.Config.class)
class RedefinicaoSenhaServiceTests {

    private static final String SENHA = "senha-antiga-123";
    private static final String NOVA = "senha-nova-456";
    private static final String IP = "203.0.113.7";
    private static final Pattern TOKEN = Pattern.compile("redefinir-senha\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private RedefinicaoSenhaService redefinicaoSenhaService;

    @Autowired
    private ContaService contaService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RedefinicaoSenhaRepository redefinicaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EnviadorEmailFalso enviador;

    @Autowired
    private LimitePedidosSenha limite;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private Clock relogio;

    private final List<Long> criados = new ArrayList<>();
    private String sufixo;
    private Usuario cliente;

    @BeforeEach
    void setUp() {
        enviador.limpar();
        limite.limpar();
        sufixo = String.valueOf(System.nanoTime());
        cliente = criar("maria-" + sufixo + "@rosie.test", Role.CLIENTE);
    }

    @AfterEach
    void limparBanco() {
        limite.limpar();
        transacao.executeWithoutResult(status -> {
            criados.forEach(redefinicaoRepository::apagarDoUsuario);
            usuarioRepository.deleteAllById(criados);
        });
    }

    @Test
    void pedidoMandaOLinkDeUsoUnicoEGuardaSoOHash() throws InterruptedException {
        redefinicaoSenhaService.solicitar("  MARIA-" + sufixo + "@Rosie.test ", IP);

        Email email = enviador.aguardar(1).get(0);
        assertThat(email.para()).isEqualTo(cliente.getEmail());
        assertThat(email.assunto()).isEqualTo("Redefinir a sua senha na Rosie");
        assertThat(email.texto()).contains("Olá, Maria!", "/redefinir-senha?token=").doesNotContain("/admin/");
        assertThat(email.html()).contains("/redefinir-senha?token=");

        String token = token(email);
        RedefinicaoSenha salvo = redefinicaoRepository.findByUsuarioId(cliente.getId()).orElseThrow();
        assertThat(salvo.getTokenHash()).isEqualTo(TokenLink.hash(token)).isNotEqualTo(token);
        assertThat(email.chave()).doesNotContain(token);
        // Prazo curto: 1 hora
        assertThat(salvo.getExpiraEm()).isBetween(LocalDateTime.now(relogio).plusMinutes(59),
                LocalDateTime.now(relogio).plusMinutes(61));
    }

    @Test
    void adminRecebeOLinkDaPaginaDoPainel() throws InterruptedException {
        Usuario admin = criar("admin-" + sufixo + "@rosie.test", Role.ADMIN);
        redefinicaoSenhaService.solicitar(admin.getEmail(), IP);

        Email email = enviador.aguardar(1).get(0);
        assertThat(email.para()).isEqualTo(admin.getEmail());
        assertThat(email.texto()).contains("/admin/redefinir-senha?token=", "painel");
    }

    @Test
    void emailSemContaTerminaIgualSemMandarNada() throws InterruptedException {
        redefinicaoSenhaService.solicitar("ninguem-" + sufixo + "@rosie.test", IP);

        assertThat(enviador.aguardar(1)).isEmpty();
        assertThat(enviador.tentativas()).isZero();
    }

    @Test
    void contaExcluidaNaoRecebeLink() throws InterruptedException {
        transacao.executeWithoutResult(status -> usuarioRepository.findById(cliente.getId()).orElseThrow()
                .setExcluidaEm(LocalDateTime.now(relogio)));

        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);

        assertThat(enviador.aguardar(1)).isEmpty();
        assertThat(redefinicaoRepository.findByUsuarioId(cliente.getId())).isEmpty();
    }

    @Test
    void redefineASenhaEncerraAsSessoesEAvisa() throws InterruptedException {
        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);
        String token = token(enviador.aguardar(1).get(0));

        redefinicaoSenhaService.redefinir(token, NOVA);

        Usuario salvo = usuarioRepository.findById(cliente.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NOVA, salvo.getSenhaHash())).isTrue();
        assertThat(salvo.getVersaoCredencial()).isEqualTo(cliente.getVersaoCredencial() + 1);
        assertThat(redefinicaoRepository.findByUsuarioId(cliente.getId())).isEmpty();
        assertThat(authService.loginCliente(new LoginRequestDTO(cliente.getEmail(), NOVA)).usuario().id())
                .isEqualTo(cliente.getId());
        assertThatThrownBy(() -> authService.loginCliente(new LoginRequestDTO(cliente.getEmail(), SENHA)))
                .isInstanceOf(CredenciaisInvalidasException.class);

        // O aviso sai pela fila, depois do commit
        List<Email> enviados = enviador.aguardar(2);
        assertThat(enviados).hasSize(2);
        Email aviso = enviados.get(1);
        assertThat(aviso.para()).isEqualTo(cliente.getEmail());
        assertThat(aviso.assunto()).isEqualTo("A sua senha na Rosie foi trocada");
        assertThat(aviso.texto()).contains("Quando:");
    }

    @Test
    void tokenUsadoNaoServeDeNovo() throws InterruptedException {
        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);
        String token = token(enviador.aguardar(1).get(0));
        redefinicaoSenhaService.redefinir(token, NOVA);

        assertThatThrownBy(() -> redefinicaoSenhaService.redefinir(token, "outra-senha-789"))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        assertThat(passwordEncoder.matches(NOVA,
                usuarioRepository.findById(cliente.getId()).orElseThrow().getSenhaHash())).isTrue();
    }

    @Test
    void tokenExpiradoNaoTrocaASenha() throws InterruptedException {
        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);
        String token = token(enviador.aguardar(1).get(0));
        transacao.executeWithoutResult(status -> redefinicaoRepository.findByUsuarioId(cliente.getId()).orElseThrow()
                .setExpiraEm(LocalDateTime.now(relogio).minusMinutes(1)));

        assertThatThrownBy(() -> redefinicaoSenhaService.redefinir(token, NOVA))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessage("Este link expirou. Peça um link novo")
                .extracting("codigo").isEqualTo("TOKEN_EXPIRADO");
        assertThat(passwordEncoder.matches(SENHA,
                usuarioRepository.findById(cliente.getId()).orElseThrow().getSenhaHash())).isTrue();
    }

    @Test
    void tokenInventadoOuVazioEhInvalido() {
        for (String token : new String[]{"token-que-nao-existe", "  ", null}) {
            assertThatThrownBy(() -> redefinicaoSenhaService.redefinir(token, NOVA))
                    .isInstanceOf(TokenInvalidoException.class)
                    .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        }
    }

    @Test
    void pedidoNovoInvalidaOLinkAnterior() throws InterruptedException {
        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);
        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);
        List<Email> enviados = enviador.aguardar(2);
        String primeiro = token(enviados.get(0));
        String segundo = token(enviados.get(1));

        assertThatThrownBy(() -> redefinicaoSenhaService.redefinir(primeiro, NOVA))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        redefinicaoSenhaService.redefinir(segundo, NOVA);
    }

    @Test
    void senhaComMaisDe72BytesNaoGastaOLink() throws InterruptedException {
        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);
        String token = token(enviador.aguardar(1).get(0));

        // 40 caracteres acentuados = 80 bytes: passa no @Size, mas o BCrypt cortaria
        assertThatThrownBy(() -> redefinicaoSenhaService.redefinir(token, "á".repeat(40)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("A senha precisa ter de 8 a 72 caracteres");
        redefinicaoSenhaService.redefinir(token, NOVA);
    }

    @Test
    void trocaDeSenhaNaMinhaContaInvalidaOLinkPendente() throws InterruptedException {
        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);
        String token = token(enviador.aguardar(1).get(0));

        contaService.trocarSenha(cliente.getId(), new SenhaRequestDTO(SENHA, NOVA));

        assertThatThrownBy(() -> redefinicaoSenhaService.redefinir(token, "outra-senha-789"))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
    }

    @Test
    void exclusaoDaContaApagaOLinkPendente() throws InterruptedException {
        redefinicaoSenhaService.solicitar(cliente.getEmail(), IP);
        String token = token(enviador.aguardar(1).get(0));

        contaService.excluir(cliente.getId(), SENHA);

        assertThat(redefinicaoRepository.findByUsuarioId(cliente.getId())).isEmpty();
        assertThatThrownBy(() -> redefinicaoSenhaService.redefinir(token, NOVA))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void limitePorEmailContaTambemEmailSemConta() {
        String semConta = "ninguem-" + sufixo + "@rosie.test";
        for (int i = 0; i < 3; i++) {
            redefinicaoSenhaService.solicitar(cliente.getEmail(), "198.51.100." + i);
            redefinicaoSenhaService.solicitar(semConta, "198.51.100." + i);
        }

        // O quarto pedido do mesmo e-mail é barrado, de qualquer IP e exista ou não a conta
        assertThatThrownBy(() -> redefinicaoSenhaService.solicitar(cliente.getEmail(), "198.51.100.99"))
                .isInstanceOf(MuitasTentativasException.class);
        assertThatThrownBy(() -> redefinicaoSenhaService.solicitar(semConta.toUpperCase(), "198.51.100.98"))
                .isInstanceOf(MuitasTentativasException.class);
    }

    @Test
    void limitePorIp() {
        for (int i = 0; i < 10; i++) {
            redefinicaoSenhaService.solicitar("varredura-" + i + "-" + sufixo + "@rosie.test", IP);
        }

        assertThatThrownBy(() -> redefinicaoSenhaService.solicitar(cliente.getEmail(), IP))
                .isInstanceOf(MuitasTentativasException.class);
        // O pedido barrado pelo IP não gastou a cota do e-mail
        redefinicaoSenhaService.solicitar(cliente.getEmail(), "198.51.100.1");
    }

    private Usuario criar(String email, Role role) {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nome("Maria Silva")
                .email(email)
                .senhaHash(passwordEncoder.encode(SENHA))
                .telefone("11987654321")
                .role(role)
                .build());
        criados.add(usuario.getId());
        return usuario;
    }

    private static String token(Email email) {
        Matcher matcher = TOKEN.matcher(email.texto());
        assertThat(matcher.find()).as("link com o token no e-mail").isTrue();
        return matcher.group(1);
    }
}
