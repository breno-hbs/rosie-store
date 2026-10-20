package com.rosie.backend.controller;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.EmailPendenteRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.EnviadorEmail.Email;
import com.rosie.backend.service.EnviadorEmailFalso;
import com.rosie.backend.service.TokenService;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// PUT /conta/email e POST /conta/email/confirmar (#61) contra o MySQL; cada teste é desfeito no fim. O link de
// confirmação sai na hora (dentro da requisição), então o EnviadorEmailFalso já o tem quando a resposta volta.
@SpringBootTest
@AutoConfigureMockMvc
@Import(EnviadorEmailFalso.Config.class)
@Transactional
class ContaEmailControllerTest {

    private static final String EMAIL = "conta-email-rota@rosie.test";
    private static final String NOVO = "conta-email-nova@rosie.test";
    private static final String SENHA = "senha-da-cliente-123";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmailPendenteRepository pendenteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EnviadorEmailFalso enviador;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock relogio;

    private Usuario cliente;
    private String tokenCliente;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        enviador.limpar();
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Silva")
                .email(EMAIL)
                .senhaHash(passwordEncoder.encode(SENHA))
                .telefone("11987654321")
                .role(Role.CLIENTE)
                .build());
        tokenCliente = tokenService.gerar(cliente);
        tokenAdmin = tokenService.gerar(usuarioRepository.save(Usuario.builder()
                .nome("Roseane")
                .email("admin-conta-email-rota@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.ADMIN)
                .build()));
    }

    @Test
    void pedidoResponde202EAContaMostraATrocaPendente() throws Exception {
        mockMvc.perform(pedir(" Conta-Email-Nova@Rosie.test ", SENHA, tokenCliente))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.novoEmail").value(NOVO))
                .andExpect(jsonPath("$.expiraEm").exists())
                // O token só vai no e-mail, nunca na resposta
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(get("/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.emailPendente.novoEmail").value(NOVO))
                .andExpect(jsonPath("$.emailPendente.expiraEm").exists());

        assertThat(enviador.aguardar(1)).extracting(Email::para).containsExactly(NOVO);
    }

    @Test
    void semTrocaPendenteOCampoVemNulo() throws Exception {
        mockMvc.perform(get("/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(jsonPath("$.emailPendente").isEmpty());
    }

    @Test
    void senhaErradaEh403SemPedirATroca() throws Exception {
        mockMvc.perform(pedir(NOVO, "senha-errada", tokenCliente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SENHA_INCORRETA"))
                .andExpect(jsonPath("$.erro").value("Senha atual incorreta"));

        assertThat(pendenteRepository.findByUsuarioId(cliente.getId())).isEmpty();
        assertThat(enviador.tentativas()).isZero();
    }

    @Test
    void emailJaCadastradoEh409MesmoDoAdminEComMaiusculas() throws Exception {
        mockMvc.perform(pedir("ADMIN-conta-email-rota@rosie.test", SENHA, tokenCliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"))
                .andExpect(jsonPath("$.erro").value("Este e-mail já está cadastrado. Entre com ele ou use outro."));
        assertThat(enviador.tentativas()).isZero();
    }

    @Test
    void dadosInvalidosDao400() throws Exception {
        for (String[] caso : List.of(
                new String[]{"{\"novoEmail\":\"\",\"senhaAtual\":\"" + SENHA + "\"}", "Informe o novo e-mail"},
                new String[]{"{\"novoEmail\":\"maria@email\",\"senhaAtual\":\"" + SENHA + "\"}", "E-mail inválido"},
                new String[]{"{\"novoEmail\":\"" + NOVO + "\",\"senhaAtual\":\"\"}", "Informe a senha atual"},
                new String[]{"{\"novoEmail\":\" " + EMAIL.toUpperCase() + "\",\"senhaAtual\":\"" + SENHA + "\"}",
                        "O novo e-mail é igual ao atual"})) {
            mockMvc.perform(put("/conta/email").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                            .contentType(MediaType.APPLICATION_JSON).content(caso[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                    .andExpect(jsonPath("$.erro").value(caso[1]));
        }
    }

    @Test
    void pedidoExigeTokenDeCliente() throws Exception {
        mockMvc.perform(put("/conta/email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novoEmail\":\"" + NOVO + "\",\"senhaAtual\":\"" + SENHA + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(pedir(NOVO, SENHA, tokenAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    void confirmacaoSemSessaoTrocaOLoginEOTokenDaSessaoContinuaValendo() throws Exception {
        String token = pedirEPegarToken();

        // Sem Authorization: o link pode ser aberto em outro aparelho
        mockMvc.perform(confirmar(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NOVO));
        entityManager.flush();

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + NOVO + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isUnauthorized());
        // O JWT não leva o e-mail: a sessão aberta antes da troca segue valendo
        mockMvc.perform(get("/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NOVO))
                .andExpect(jsonPath("$.emailPendente").isEmpty());
    }

    @Test
    void tokenUsadoInventadoOuAusenteEh400TokenInvalido() throws Exception {
        String token = pedirEPegarToken();
        mockMvc.perform(confirmar(token)).andExpect(status().isOk());

        for (String corpo : List.of("{\"token\":\"" + token + "\"}", "{\"token\":\"inventado\"}", "{}")) {
            mockMvc.perform(post("/conta/email/confirmar").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"))
                    .andExpect(jsonPath("$.erro").value("Este link não é válido ou já foi usado"));
        }
    }

    @Test
    void tokenExpiradoEh400TokenExpirado() throws Exception {
        String token = pedirEPegarToken();
        pendenteRepository.findByUsuarioId(cliente.getId()).orElseThrow()
                .setExpiraEm(LocalDateTime.now(relogio).minusSeconds(1));
        entityManager.flush();

        mockMvc.perform(confirmar(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_EXPIRADO"));
    }

    @Test
    void emailCadastradoAntesDaConfirmacaoEh409() throws Exception {
        String token = pedirEPegarToken();
        usuarioRepository.save(Usuario.builder()
                .nome("Outra").email(NOVO).senhaHash("hash-irrelevante").role(Role.CLIENTE).build());

        mockMvc.perform(confirmar(token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));
    }

    @Test
    void falhaNoEnvioDoLinkEh502() throws Exception {
        enviador.falharCom(new com.rosie.backend.exception.EmailFalhouException("Resend fora do ar"));

        mockMvc.perform(pedir(NOVO, SENHA, tokenCliente))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("EMAIL_FALHOU"));
        assertThat(pendenteRepository.findByUsuarioId(cliente.getId())).isEmpty();
    }

    private String pedirEPegarToken() throws Exception {
        mockMvc.perform(pedir(NOVO, SENHA, tokenCliente)).andExpect(status().isAccepted());
        Matcher matcher = TOKEN.matcher(enviador.aguardar(1).get(0).texto());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static MockHttpServletRequestBuilder pedir(String novoEmail, String senha, String token) {
        return put("/conta/email")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novoEmail\":\"" + novoEmail + "\",\"senhaAtual\":\"" + senha + "\"}");
    }

    private static MockHttpServletRequestBuilder confirmar(String token) {
        return post("/conta/email/confirmar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }
}
