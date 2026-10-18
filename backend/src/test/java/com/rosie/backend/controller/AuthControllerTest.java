package com.rosie.backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.PoliticaPrivacidade;
import com.rosie.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cadastro e login de cliente (RF08) contra o MySQL: cada teste roda numa transação desfeita no fim
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    private static final String EMAIL_NOVO = "maria-teste@rosie.test";
    private static final String SENHA_NOVA = "senha-da-maria-123";
    private static final String EMAIL_CLIENTE = "cliente-existente@rosie.test";
    private static final String SENHA_CLIENTE = "senha-do-cliente-123";
    private static final String EMAIL_ADMIN = "admin-loja@rosie.test";
    private static final String SENHA_ADMIN = "senha-do-admin-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JsonMapper jsonMapper;

    private Usuario cliente;
    private Usuario admin;

    @BeforeEach
    void criarUsuarios() {
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Existente")
                .email(EMAIL_CLIENTE)
                .senhaHash(passwordEncoder.encode(SENHA_CLIENTE))
                .role(Role.CLIENTE)
                .build());
        admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email(EMAIL_ADMIN)
                .senhaHash(passwordEncoder.encode(SENHA_ADMIN))
                .role(Role.ADMIN)
                .build());
    }

    // ---- Cadastro

    @Test
    void cadastroCriaClienteEDevolveTokenDeDuasHoras() throws Exception {
        String corpo = mockMvc.perform(registrar(cadastroValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").isNumber())
                .andExpect(jsonPath("$.usuario.nome").value("Maria Teste"))
                .andExpect(jsonPath("$.usuario.role").value("CLIENTE"))
                .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Usuario salvo = usuarioRepository.findByEmail(EMAIL_NOVO).orElseThrow();
        assertThat(salvo.getRole()).isEqualTo(Role.CLIENTE);
        assertThat(salvo.getTelefone()).isEqualTo("11987654321");

        Jwt jwt = jwtDecoder.decode(JsonPath.read(corpo, "$.token"));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(salvo.getId()));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CLIENTE");
        assertThat(jwt.getClaimAsString("nome")).isEqualTo("Maria Teste");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void cadastroGuardaSenhaComHashBCrypt() throws Exception {
        mockMvc.perform(registrar(cadastroValido())).andExpect(status().isCreated());

        String hash = usuarioRepository.findByEmail(EMAIL_NOVO).orElseThrow().getSenhaHash();
        assertThat(hash).isNotEqualTo(SENHA_NOVA).startsWith("$2");
        assertThat(passwordEncoder.matches(SENHA_NOVA, hash)).isTrue();
    }

    @Test
    void cadastroGuardaDataEVersaoDoAceiteDaPolitica() throws Exception {
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        mockMvc.perform(registrar(cadastroValido())).andExpect(status().isCreated());

        Usuario salvo = usuarioRepository.findByEmail(EMAIL_NOVO).orElseThrow();
        assertThat(salvo.getPrivacidadeVersao()).isEqualTo(PoliticaPrivacidade.VERSAO);
        assertThat(salvo.getPrivacidadeAceitaEm()).isAfter(antes).isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    void cadastroNormalizaEmailETiraEspacosDoNome() throws Exception {
        Map<String, Object> dados = cadastroValido();
        dados.put("email", "  Maria-Teste@Rosie.TEST ");
        dados.put("nome", "  Maria Teste  ");

        mockMvc.perform(registrar(dados))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.nome").value("Maria Teste"));

        assertThat(usuarioRepository.findByEmail(EMAIL_NOVO)).isPresent();
    }

    @Test
    void emailJaCadastradoRetorna409() throws Exception {
        Map<String, Object> dados = cadastroValido();
        dados.put("email", EMAIL_CLIENTE);

        mockMvc.perform(registrar(dados))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Este e-mail já está cadastrado. Entre com ele ou use outro."))
                .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));
    }

    @Test
    void emailJaCadastradoComMaiusculasTambemRetorna409() throws Exception {
        Map<String, Object> dados = cadastroValido();
        dados.put("email", "Cliente-Existente@ROSIE.test");

        mockMvc.perform(registrar(dados))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));
    }

    @Test
    void cadastroSemAceiteDaPoliticaRetorna400() throws Exception {
        Map<String, Object> recusou = cadastroValido();
        recusou.put("aceitePrivacidade", false);
        Map<String, Object> semCampo = cadastroValido();
        semCampo.remove("aceitePrivacidade");

        for (Map<String, Object> dados : List.of(recusou, semCampo)) {
            mockMvc.perform(registrar(dados))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value("Aceite a política de privacidade para criar a conta"))
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        }
        assertThat(usuarioRepository.findByEmail(EMAIL_NOVO)).isEmpty();
    }

    @Test
    void cadastroComCampoInvalidoRetorna400ComAMensagemDoCampo() throws Exception {
        Map<String, String> casos = Map.of(
                "nome", "Informe o nome",
                "email", "E-mail inválido",
                "senha", "A senha precisa ter de 8 a 72 caracteres",
                "telefone", "Telefone inválido: informe DDD e número, só com dígitos");
        Map<String, Object> valores = Map.of(
                "nome", "   ",
                "email", "maria@email",
                "senha", "1234567",
                "telefone", "(11) 98765-4321");

        for (var caso : casos.entrySet()) {
            Map<String, Object> dados = cadastroValido();
            dados.put(caso.getKey(), valores.get(caso.getKey()));

            mockMvc.perform(registrar(dados))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erro").value(caso.getValue()))
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        }
    }

    @Test
    void senhaAcimaDe72BytesRetorna400() throws Exception {
        Map<String, Object> dados = cadastroValido();
        dados.put("senha", "ç".repeat(40)); // 40 caracteres, 80 bytes

        mockMvc.perform(registrar(dados))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A senha precisa ter de 8 a 72 caracteres"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    // ---- Login

    @Test
    void loginCertoDevolveTokenDeCliente() throws Exception {
        String corpo = mockMvc.perform(login(EMAIL_CLIENTE, SENHA_CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(cliente.getId()))
                .andExpect(jsonPath("$.usuario.nome").value("Cliente Existente"))
                .andExpect(jsonPath("$.usuario.role").value("CLIENTE"))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(corpo, "$.token"));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CLIENTE");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void loginIgnoraMaiusculasEEspacosNoEmail() throws Exception {
        mockMvc.perform(login(" Cliente-Existente@Rosie.TEST ", SENHA_CLIENTE))
                .andExpect(status().isOk());
    }

    @Test
    void clienteRecemCadastradoConsegueEntrar() throws Exception {
        mockMvc.perform(registrar(cadastroValido())).andExpect(status().isCreated());

        mockMvc.perform(login(EMAIL_NOVO, SENHA_NOVA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nome").value("Maria Teste"));
    }

    @Test
    void senhaErradaRetorna401CredenciaisInvalidas() throws Exception {
        mockMvc.perform(login(EMAIL_CLIENTE, "senha-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha incorretos"))
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void emailInexistenteRespondeIgualASenhaErrada() throws Exception {
        String senhaErrada = mockMvc.perform(login(EMAIL_CLIENTE, "senha-errada"))
                .andReturn().getResponse().getContentAsString();

        String emailInexistente = mockMvc.perform(login("ninguem@rosie.test", SENHA_CLIENTE))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(emailInexistente).isEqualTo(senhaErrada);
    }

    // O BCrypt não compara mais de 72 bytes: o login precisa responder 401, não 500
    @Test
    void senhaLongaDemaisNoLoginRetorna401() throws Exception {
        mockMvc.perform(login(EMAIL_CLIENTE, "x".repeat(100)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    // Cada um pelo seu login: a Roseane entra em /admin/auth/login
    @Test
    void adminNaoEntraPeloLoginDaLoja() throws Exception {
        mockMvc.perform(login(EMAIL_ADMIN, SENHA_ADMIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void loginComEmailMalFormadoRetorna400() throws Exception {
        mockMvc.perform(login("nao-e-email", SENHA_CLIENTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    // ---- Rotas protegidas

    @Test
    void meComTokenDoCadastroDevolveOCliente() throws Exception {
        String corpo = mockMvc.perform(registrar(cadastroValido()))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.token");

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Teste"))
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void meSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void meComTokenDeAdminRetorna403() throws Exception {
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokenService.gerar(admin))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    void tokenDeClienteEhBarradoNoPainel() throws Exception {
        String corpo = mockMvc.perform(login(EMAIL_CLIENTE, SENHA_CLIENTE))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.token");

        for (String rota : new String[] {"/admin/produtos", "/admin/categorias", "/admin/auth/me"}) {
            mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        }
    }

    // As rotas da compra ainda não existem, mas já exigem token de cliente
    @Test
    void rotasDaCompraExigemTokenDeCliente() throws Exception {
        for (String rota : new String[] {"/carrinho", "/pedidos", "/trocas"}) {
            mockMvc.perform(get(rota))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
            mockMvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, bearer(tokenService.gerar(admin))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        }
    }

    private Map<String, Object> cadastroValido() {
        Map<String, Object> dados = new HashMap<>();
        dados.put("nome", "Maria Teste");
        dados.put("email", EMAIL_NOVO);
        dados.put("senha", SENHA_NOVA);
        dados.put("telefone", "11987654321");
        dados.put("aceitePrivacidade", true);
        return dados;
    }

    private RequestBuilder registrar(Map<String, Object> dados) {
        return post("/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(dados));
    }

    private RequestBuilder login(String email, String senha) {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
