package com.rosie.backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.rosie.backend.config.JwtConfig;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fluxo completo contra o MySQL: cada teste roda numa transação desfeita no fim
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuthControllerTest {

    private static final String EMAIL_ADMIN = "admin-teste@rosie.test";
    private static final String SENHA_ADMIN = "senha-do-admin-123";
    private static final String EMAIL_CLIENTE = "cliente-teste@rosie.test";
    private static final String SENHA_CLIENTE = "senha-do-cliente-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private Usuario admin;
    private Usuario cliente;

    @BeforeEach
    void criarUsuarios() {
        admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email(EMAIL_ADMIN)
                .senhaHash(passwordEncoder.encode(SENHA_ADMIN))
                .role(Role.ADMIN)
                .build());
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Teste")
                .email(EMAIL_CLIENTE)
                .senhaHash(passwordEncoder.encode(SENHA_CLIENTE))
                .role(Role.CLIENTE)
                .build());
    }

    @Test
    void loginComCredenciaisCorretasDevolveTokenEUsuario() throws Exception {
        String corpo = mockMvc.perform(login(EMAIL_ADMIN, SENHA_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value(admin.getId()))
                .andExpect(jsonPath("$.usuario.nome").value("Roseane Teste"))
                .andExpect(jsonPath("$.usuario.role").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.email").doesNotExist())
                .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(corpo, "$.token"));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(admin.getId()));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void loginIgnoraMaiusculasNoEmail() throws Exception {
        mockMvc.perform(login("Admin-Teste@Rosie.TEST", SENHA_ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void senhaErradaRetorna401CredenciaisInvalidas() throws Exception {
        mockMvc.perform(login(EMAIL_ADMIN, "senha-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha incorretos"))
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void emailInexistenteRespondeIgualASenhaErrada() throws Exception {
        String senhaErrada = mockMvc.perform(login(EMAIL_ADMIN, "senha-errada"))
                .andReturn().getResponse().getContentAsString();

        String emailInexistente = mockMvc.perform(login("ninguem@rosie.test", SENHA_ADMIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(emailInexistente).isEqualTo(senhaErrada);
    }

    @Test
    void clienteNaoEntraNoPainel() throws Exception {
        mockMvc.perform(login(EMAIL_CLIENTE, SENHA_CLIENTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void emailMalFormadoRetorna400() throws Exception {
        mockMvc.perform(login("nao-e-email", SENHA_ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("E-mail inválido"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void corpoAusenteRetorna400() throws Exception {
        mockMvc.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void senhaFicaGuardadaComHashBCrypt() {
        String hash = usuarioRepository.findByEmail(EMAIL_ADMIN).orElseThrow().getSenhaHash();

        assertThat(hash).isNotEqualTo(SENHA_ADMIN).startsWith("$2");
        assertThat(passwordEncoder.matches(SENHA_ADMIN, hash)).isTrue();
    }

    @Test
    void rotaAdminSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/admin/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Autenticação necessária"))
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void rotaAdminComTokenDeClienteRetorna403() throws Exception {
        mockMvc.perform(get("/admin/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokenService.gerar(cliente))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Acesso negado"))
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    void rotaAdminComTokenDoLoginRetorna200() throws Exception {
        String corpo = mockMvc.perform(login(EMAIL_ADMIN, SENHA_ADMIN))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.token");

        mockMvc.perform(get("/admin/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.nome").value("Roseane Teste"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        Instant tresHorasAtras = Instant.now().minus(Duration.ofHours(3));
        String expirado = assinar(jwtEncoder, tresHorasAtras, tresHorasAtras.plus(Duration.ofHours(2)));

        mockMvc.perform(get("/admin/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(expirado)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void tokenAssinadoComOutroSegredoRetorna401() throws Exception {
        byte[] outroSegredo = "outro-segredo-qualquer-com-mais-de-32-caracteres".getBytes(StandardCharsets.UTF_8);
        JwtEncoder outroEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(outroSegredo, "HmacSHA256")).build();
        Instant agora = Instant.now();
        String falsificado = assinar(outroEncoder, agora, agora.plus(Duration.ofHours(2)));

        mockMvc.perform(get("/admin/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(falsificado)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    private RequestBuilder login(String email, String senha) {
        return post("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha));
    }

    // Token de admin com datas escolhidas pelo teste
    private String assinar(JwtEncoder encoder, Instant emitidoEm, Instant expiraEm) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.EMISSOR)
                .subject(String.valueOf(admin.getId()))
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm)
                .claim("nome", admin.getNome())
                .claim("role", "ADMIN")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
