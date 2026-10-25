package com.rosie.backend.controller;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.RedefinicaoSenhaRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.EnviadorEmail.Email;
import com.rosie.backend.service.EnviadorEmailFalso;
import com.rosie.backend.service.LimitePedidosSenha;
import com.rosie.backend.service.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /auth/esqueci-senha e POST /auth/redefinir-senha (#93) contra o MySQL, sem a transação do teste: o link
// sai pela FilaEmail depois do commit, e o teste o pega no EnviadorEmailFalso. Apaga tudo no fim.
@SpringBootTest
@AutoConfigureMockMvc
@Import(EnviadorEmailFalso.Config.class)
class RedefinicaoSenhaControllerTest {

    private static final String SENHA = "senha-antiga-123";
    private static final String NOVA = "senha-nova-456";
    private static final Pattern TOKEN = Pattern.compile("redefinir-senha\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RedefinicaoSenhaRepository redefinicaoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

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
    private Usuario admin;

    @BeforeEach
    void setUp() {
        enviador.limpar();
        limite.limpar();
        sufixo = String.valueOf(System.nanoTime());
        cliente = criar("cliente-rota-" + sufixo + "@rosie.test", Role.CLIENTE);
        admin = criar("admin-rota-" + sufixo + "@rosie.test", Role.ADMIN);
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
    void emailExistenteEInexistenteTemAMesmaResposta() throws Exception {
        String existente = mockMvc.perform(esqueci(cliente.getEmail()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(AuthController.MENSAGEM_LINK_ENVIADO))
                // O token só vai no e-mail, nunca na resposta
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String inexistente = mockMvc.perform(esqueci("ninguem-" + sufixo + "@rosie.test"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(inexistente).isEqualTo(existente);
        assertThat(enviador.aguardar(1)).extracting(Email::para).containsExactly(cliente.getEmail());
    }

    @Test
    void emailInvalidoDa400() throws Exception {
        mockMvc.perform(esqueci(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erro").value("Informe o e-mail"));
        mockMvc.perform(esqueci("maria@email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("E-mail inválido"));
    }

    @Test
    void redefineASenhaDaClienteEEncerraASessaoAntiga() throws Exception {
        String sessaoAntiga = tokenService.gerar(cliente);
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sessaoAntiga))
                .andExpect(status().isOk());

        mockMvc.perform(esqueci(cliente.getEmail())).andExpect(status().isAccepted());
        mockMvc.perform(redefinir(token(enviador.aguardar(1).get(0)), NOVA))
                .andExpect(status().isNoContent());

        // O token emitido antes da troca deixa de valer na hora
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sessaoAntiga))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(login("/auth/login", cliente.getEmail(), SENHA)).andExpect(status().isUnauthorized());
        String resposta = mockMvc.perform(login("/auth/login", cliente.getEmail(), NOVA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String sessaoNova = resposta.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sessaoNova))
                .andExpect(status().isOk());
    }

    @Test
    void redefineASenhaDoAdminEEncerraASessaoDoPainel() throws Exception {
        String sessaoAntiga = tokenService.gerar(admin);

        mockMvc.perform(esqueci(admin.getEmail())).andExpect(status().isAccepted());
        Email email = enviador.aguardar(1).get(0);
        assertThat(email.texto()).contains("/admin/redefinir-senha?token=");
        mockMvc.perform(redefinir(token(email), NOVA)).andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sessaoAntiga))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("/admin/auth/login", admin.getEmail(), NOVA)).andExpect(status().isOk());
    }

    @Test
    void tokenUsadoExpiradoOuInvalidoDa400() throws Exception {
        mockMvc.perform(esqueci(cliente.getEmail())).andExpect(status().isAccepted());
        String token = token(enviador.aguardar(1).get(0));
        mockMvc.perform(redefinir(token, NOVA)).andExpect(status().isNoContent());

        mockMvc.perform(redefinir(token, "outra-senha-789"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"))
                .andExpect(jsonPath("$.erro").value("Este link não é válido ou já foi usado"));
        mockMvc.perform(redefinir("token-que-nao-existe", NOVA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
        mockMvc.perform(post("/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaSenha\":\"" + NOVA + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));

        // Espera o aviso da troca sair antes de pedir o link seguinte
        enviador.aguardar(2);
        enviador.limpar();
        mockMvc.perform(esqueci(cliente.getEmail())).andExpect(status().isAccepted());
        String vencido = token(enviador.aguardar(1).get(0));
        transacao.executeWithoutResult(status -> redefinicaoRepository.findByUsuarioId(cliente.getId()).orElseThrow()
                .setExpiraEm(LocalDateTime.now(relogio).minusMinutes(1)));
        mockMvc.perform(redefinir(vencido, NOVA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_EXPIRADO"));
    }

    @Test
    void novaSenhaForaDasRegrasDoCadastroDa400() throws Exception {
        mockMvc.perform(redefinir("qualquer", "curta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erro").value("A senha precisa ter de 8 a 72 caracteres"));
        mockMvc.perform(post("/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"qualquer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe a nova senha"));
    }

    @Test
    void passarDoLimiteDa429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(esqueci(cliente.getEmail()).with(ip("198.51.100." + i))).andExpect(status().isAccepted());
        }
        mockMvc.perform(esqueci(cliente.getEmail()).with(ip("198.51.100.50")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(esqueci("varredura-" + i + "-" + sufixo + "@rosie.test").with(ip("203.0.113.9")))
                    .andExpect(status().isAccepted());
        }
        mockMvc.perform(esqueci(admin.getEmail()).with(ip("203.0.113.9")))
                .andExpect(status().isTooManyRequests());
    }

    private static MockHttpServletRequestBuilder esqueci(String email) {
        return post("/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}");
    }

    private static MockHttpServletRequestBuilder redefinir(String token, String novaSenha) {
        return post("/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"" + novaSenha + "\"}");
    }

    private static MockHttpServletRequestBuilder login(String caminho, String email, String senha) {
        return post(caminho).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}");
    }

    private static RequestPostProcessor ip(String endereco) {
        return request -> {
            request.setRemoteAddr(endereco);
            return request;
        };
    }

    private Usuario criar(String email, Role role) {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nome(role == Role.ADMIN ? "Roseane" : "Maria Silva")
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
