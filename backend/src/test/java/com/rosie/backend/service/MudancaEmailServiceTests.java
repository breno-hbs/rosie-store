package com.rosie.backend.service;

import com.rosie.backend.dto.ContaDTO;
import com.rosie.backend.dto.EmailConfirmadoDTO;
import com.rosie.backend.dto.EmailPendenteDTO;
import com.rosie.backend.dto.EmailRequestDTO;
import com.rosie.backend.dto.LoginRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.CredenciaisInvalidasException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.EmailFalhouException;
import com.rosie.backend.exception.SenhaIncorretaException;
import com.rosie.backend.exception.TokenInvalidoException;
import com.rosie.backend.model.EmailPendente;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.EmailPendenteRepository;
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

// Troca de e-mail da Minha Conta (#61) contra o MySQL, sem a transação do teste: o aviso ao e-mail antigo só sai
// depois do commit. O link chega pelo EnviadorEmailFalso, de onde o teste tira o token. Apaga tudo no fim.
@SpringBootTest
@Import(EnviadorEmailFalso.Config.class)
class MudancaEmailServiceTests {

    private static final String SENHA = "senha-da-cliente-123";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MudancaEmailService mudancaEmailService;

    @Autowired
    private ContaService contaService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmailPendenteRepository pendenteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EnviadorEmailFalso enviador;

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
        sufixo = String.valueOf(System.nanoTime());
        cliente = criar("maria-" + sufixo + "@rosie.test", Role.CLIENTE);
    }

    @AfterEach
    void limparBanco() {
        transacao.executeWithoutResult(status -> {
            criados.forEach(pendenteRepository::apagarDaCliente);
            usuarioRepository.deleteAllById(criados);
        });
    }

    @Test
    void pedidoGuardaATrocaPendenteEMandaOLinkAoEmailNovo() throws InterruptedException {
        EmailPendenteDTO pendente = solicitar("  Maria.Nova-" + sufixo + "@ROSIE.test ");

        String novo = "maria.nova-" + sufixo + "@rosie.test";
        assertThat(pendente.novoEmail()).isEqualTo(novo);
        assertThat(pendente.expiraEm()).isAfter(LocalDateTime.now(relogio).plusHours(23));

        Email email = enviador.aguardar(1).get(0);
        assertThat(email.para()).isEqualTo(novo);
        assertThat(email.assunto()).isEqualTo("Confirme o seu novo e-mail na Rosie");
        assertThat(email.texto()).contains("Olá, Maria!", "/minha-conta/confirmar-email?token=");
        assertThat(email.html()).contains("/minha-conta/confirmar-email?token=");

        // No banco fica só o hash do token, nunca o token do link
        String token = token(email);
        EmailPendente salvo = pendenteRepository.findByUsuarioId(cliente.getId()).orElseThrow();
        assertThat(salvo.getTokenHash()).isEqualTo(MudancaEmailService.hash(token)).isNotEqualTo(token);
        assertThat(email.chave()).doesNotContain(token);

        // A Minha Conta mostra a troca pendente; o e-mail da conta ainda é o antigo
        ContaDTO conta = contaService.buscar(cliente.getId());
        assertThat(conta.email()).isEqualTo(cliente.getEmail());
        assertThat(conta.emailPendente()).isEqualTo(pendente);
    }

    @Test
    void senhaErradaNaoPedeATroca() throws InterruptedException {
        assertThatThrownBy(() -> mudancaEmailService.solicitar(cliente.getId(),
                new EmailRequestDTO("nova-" + sufixo + "@rosie.test", "senha-errada")))
                .isInstanceOf(SenhaIncorretaException.class);

        assertThat(pendenteRepository.findByUsuarioId(cliente.getId())).isEmpty();
        assertThat(enviador.tentativas()).isZero();
    }

    @Test
    void emailDeOutraClienteEh409MesmoComMaiusculas() {
        criar("outra-" + sufixo + "@rosie.test", Role.CLIENTE);

        assertThatThrownBy(() -> solicitar("OUTRA-" + sufixo + "@Rosie.Test"))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("EMAIL_JA_CADASTRADO");
        assertThat(pendenteRepository.findByUsuarioId(cliente.getId())).isEmpty();
        assertThat(enviador.tentativas()).isZero();
    }

    @Test
    void emailDoAdminEh409() {
        criar("admin-" + sufixo + "@rosie.test", Role.ADMIN);

        assertThatThrownBy(() -> solicitar("Admin-" + sufixo + "@rosie.test"))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("EMAIL_JA_CADASTRADO");
    }

    @Test
    void emailIgualAoAtualEh400() {
        assertThatThrownBy(() -> solicitar(" " + cliente.getEmail().toUpperCase() + " "))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("O novo e-mail é igual ao atual");
    }

    @Test
    void loginAntigoValeAteConfirmarEONovoDepois() throws InterruptedException {
        String antigo = cliente.getEmail();
        String novo = "maria.nova-" + sufixo + "@rosie.test";
        solicitar(novo);
        String token = token(enviador.aguardar(1).get(0));

        assertThat(authService.loginCliente(new LoginRequestDTO(antigo, SENHA)).usuario().id())
                .isEqualTo(cliente.getId());
        assertThatThrownBy(() -> authService.loginCliente(new LoginRequestDTO(novo, SENHA)))
                .isInstanceOf(CredenciaisInvalidasException.class);

        EmailConfirmadoDTO confirmado = mudancaEmailService.confirmar(token);

        assertThat(confirmado.email()).isEqualTo(novo);
        assertThat(usuarioRepository.findById(cliente.getId()).orElseThrow().getEmail()).isEqualTo(novo);
        assertThat(authService.loginCliente(new LoginRequestDTO(novo, SENHA)).usuario().id())
                .isEqualTo(cliente.getId());
        assertThatThrownBy(() -> authService.loginCliente(new LoginRequestDTO(antigo, SENHA)))
                .isInstanceOf(CredenciaisInvalidasException.class);
        assertThat(contaService.buscar(cliente.getId()).emailPendente()).isNull();
    }

    @Test
    void confirmarAvisaOEmailAntigo() throws InterruptedException {
        String antigo = cliente.getEmail();
        String novo = "maria.nova-" + sufixo + "@rosie.test";
        solicitar(novo);
        mudancaEmailService.confirmar(token(enviador.aguardar(1).get(0)));

        // O aviso sai pela FilaEmail, depois do commit
        List<Email> enviados = enviador.aguardar(2);
        assertThat(enviados).hasSize(2);
        Email aviso = enviados.get(1);
        assertThat(aviso.para()).isEqualTo(antigo);
        assertThat(aviso.assunto()).isEqualTo("O e-mail da sua conta na Rosie foi trocado");
        // O e-mail novo vai mascarado no aviso
        assertThat(aviso.texto()).contains("Novo e-mail: " + Email.mascarar(novo)).doesNotContain(novo);
    }

    @Test
    void tokenUsadoNaoServeDeNovo() throws InterruptedException {
        solicitar("maria.nova-" + sufixo + "@rosie.test");
        String token = token(enviador.aguardar(1).get(0));
        mudancaEmailService.confirmar(token);

        assertThatThrownBy(() -> mudancaEmailService.confirmar(token))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
    }

    @Test
    void tokenExpiradoNaoTrocaOEmail() throws InterruptedException {
        solicitar("maria.nova-" + sufixo + "@rosie.test");
        String token = token(enviador.aguardar(1).get(0));
        transacao.executeWithoutResult(status -> pendenteRepository.findByUsuarioId(cliente.getId()).orElseThrow()
                .setExpiraEm(LocalDateTime.now(relogio).minusMinutes(1)));

        assertThatThrownBy(() -> mudancaEmailService.confirmar(token))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_EXPIRADO");
        assertThat(usuarioRepository.findById(cliente.getId()).orElseThrow().getEmail()).isEqualTo(cliente.getEmail());
        // Vencida, a troca não aparece mais como pendente na Minha Conta
        assertThat(contaService.buscar(cliente.getId()).emailPendente()).isNull();
    }

    @Test
    void tokenInventadoOuVazioEhInvalido() {
        assertThatThrownBy(() -> mudancaEmailService.confirmar("token-que-nao-existe"))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        assertThatThrownBy(() -> mudancaEmailService.confirmar("  "))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        assertThatThrownBy(() -> mudancaEmailService.confirmar(null))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void pedidoNovoInvalidaOLinkAnterior() throws InterruptedException {
        solicitar("primeiro-" + sufixo + "@rosie.test");
        solicitar("segundo-" + sufixo + "@rosie.test");
        List<Email> enviados = enviador.aguardar(2);
        String primeiro = token(enviados.get(0));
        String segundo = token(enviados.get(1));

        assertThatThrownBy(() -> mudancaEmailService.confirmar(primeiro))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
        assertThat(mudancaEmailService.confirmar(segundo).email()).isEqualTo("segundo-" + sufixo + "@rosie.test");
    }

    @Test
    void emailCadastradoNoMeioTempoEh409NaConfirmacao() throws InterruptedException {
        String novo = "maria.nova-" + sufixo + "@rosie.test";
        solicitar(novo);
        String token = token(enviador.aguardar(1).get(0));
        criar(novo, Role.CLIENTE);

        assertThatThrownBy(() -> mudancaEmailService.confirmar(token))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("EMAIL_JA_CADASTRADO");
        assertThat(usuarioRepository.findById(cliente.getId()).orElseThrow().getEmail()).isEqualTo(cliente.getEmail());
    }

    @Test
    void falhaNoEnvioDoLinkDesfazOPedido() {
        enviador.falharCom(new EmailFalhouException("O Resend recusou o e-mail (HTTP 500)"));

        assertThatThrownBy(() -> solicitar("maria.nova-" + sufixo + "@rosie.test"))
                .isInstanceOf(EmailFalhouException.class);
        assertThat(pendenteRepository.findByUsuarioId(cliente.getId())).isEmpty();
    }

    @Test
    void exclusaoDaContaApagaATrocaPendente() throws InterruptedException {
        solicitar("maria.nova-" + sufixo + "@rosie.test");
        String token = token(enviador.aguardar(1).get(0));

        contaService.excluir(cliente.getId(), SENHA);

        assertThat(pendenteRepository.findByUsuarioId(cliente.getId())).isEmpty();
        assertThatThrownBy(() -> mudancaEmailService.confirmar(token))
                .isInstanceOf(TokenInvalidoException.class)
                .extracting("codigo").isEqualTo("TOKEN_INVALIDO");
    }

    private EmailPendenteDTO solicitar(String novoEmail) {
        return mudancaEmailService.solicitar(cliente.getId(), new EmailRequestDTO(novoEmail, SENHA));
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
