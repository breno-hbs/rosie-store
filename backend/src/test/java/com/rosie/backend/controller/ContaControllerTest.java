package com.rosie.backend.controller;

import com.rosie.backend.model.Role;
import com.rosie.backend.service.PoliticaPrivacidade;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.TokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET/PUT /conta e PUT /conta/senha (#26), aceite da política e exclusão (#31) contra o MySQL, com token de cliente
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContaControllerTest {

    private static final String EMAIL = "conta-rota@rosie.test";
    private static final String SENHA = "senha-da-cliente-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EntityManager entityManager;

    private Usuario cliente;
    private String tokenCliente;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
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
                .email("admin-conta-rota@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.ADMIN)
                .build()));
    }

    @Test
    void mostraOsDadosDaClienteDoToken() throws Exception {
        mockMvc.perform(get("/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cliente.getId()))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.telefone").value("11987654321"))
                // Nada de hash de senha nem role na resposta
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void atualizaNomeETelefone() throws Exception {
        mockMvc.perform(atualizar("{\"nome\":\" Maria Souza \",\"telefone\":\"1134567890\"}", tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.telefone").value("1134567890"))
                .andExpect(jsonPath("$.email").value(EMAIL));

        mockMvc.perform(get("/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(jsonPath("$.nome").value("Maria Souza"));
    }

    @Test
    void dadosInvalidosDao400() throws Exception {
        for (String[] caso : List.of(
                new String[]{"{\"nome\":\"\",\"telefone\":\"11987654321\"}", "Informe o nome"},
                new String[]{"{\"nome\":\"Maria\",\"telefone\":\"\"}", "Informe o telefone"},
                new String[]{"{\"nome\":\"Maria\",\"telefone\":\"   \"}", "Informe o telefone"},
                new String[]{"{\"nome\":\"\",\"telefone\":\"\"}", "Informe o nome"},
                new String[]{"{\"nome\":\"Maria\",\"telefone\":\"(11) 98765-4321\"}",
                        "Telefone inválido: informe DDD e número, só com dígitos"},
                new String[]{"{\"nome\":\"" + "a".repeat(151) + "\",\"telefone\":\"11987654321\"}",
                        "O nome pode ter no máximo 150 caracteres"})) {
            mockMvc.perform(atualizar(caso[0], tokenCliente))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                    .andExpect(jsonPath("$.erro").value(caso[1]));
        }
        mockMvc.perform(atualizar("{}", tokenCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe o nome"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void trocaASenhaEAsenhaNovaEntraNoLogin() throws Exception {
        mockMvc.perform(trocarSenha("{\"senhaAtual\":\"" + SENHA + "\",\"novaSenha\":\"nova-senha-456\"}", tokenCliente))
                .andExpect(status().isNoContent());
        entityManager.flush();

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"nova-senha-456\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void senhaAtualErradaOuNovaCurtaDao400() throws Exception {
        mockMvc.perform(trocarSenha("{\"senhaAtual\":\"errada-123\",\"novaSenha\":\"nova-senha-456\"}", tokenCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erro").value("Senha atual incorreta"));
        mockMvc.perform(trocarSenha("{\"senhaAtual\":\"" + SENHA + "\",\"novaSenha\":\"curta\"}", tokenCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A senha precisa ter de 8 a 72 caracteres"));
        mockMvc.perform(trocarSenha("{\"novaSenha\":\"nova-senha-456\"}", tokenCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe a senha atual"));

        entityManager.flush();
        entityManager.clear();
        String hash = usuarioRepository.findById(cliente.getId()).orElseThrow().getSenhaHash();
        assertThat(passwordEncoder.matches(SENHA, hash)).isTrue();
    }

    @Test
    void semTokenDa401ETokenDeAdminDa403() throws Exception {
        mockMvc.perform(get("/conta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(put("/conta/senha").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        mockMvc.perform(atualizar("{\"nome\":\"Roseane\",\"telefone\":\"11987654321\"}", tokenAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void versaoDaPoliticaEhPublicaEIgualAQueOCadastroGrava() throws Exception {
        mockMvc.perform(get("/privacidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(PoliticaPrivacidade.VERSAO))
                .andExpect(jsonPath("$.vigencia").value(PoliticaPrivacidade.VIGENCIA.toString()));
    }

    @Test
    void loginComPoliticaAntigaPedeONovoAceite() throws Exception {
        cliente.setPrivacidadeVersao("2020-01-01");
        entityManager.flush();

        mockMvc.perform(login(SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacidadePendente").value(true));

        mockMvc.perform(post("/conta/privacidade").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aceitePrivacidade\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacidadeVersao").value(PoliticaPrivacidade.VERSAO))
                .andExpect(jsonPath("$.privacidadeAceitaEm").isNotEmpty());
        entityManager.flush();

        mockMvc.perform(login(SENHA))
                .andExpect(jsonPath("$.privacidadePendente").value(false));
    }

    @Test
    void aceiteSemMarcarDa400() throws Exception {
        mockMvc.perform(post("/conta/privacidade").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aceitePrivacidade\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Aceite a política de privacidade para continuar"));
    }

    @Test
    void exclusaoComASenhaCertaDerrubaOTokenEOLogin() throws Exception {
        mockMvc.perform(excluir("{\"senha\":\"" + SENHA + "\"}", tokenCliente))
                .andExpect(status().isNoContent());
        entityManager.flush();

        // O token emitido antes da exclusão para de valer na hora
        mockMvc.perform(get("/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(login(SENHA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));

        entityManager.clear();
        Usuario excluida = usuarioRepository.findById(cliente.getId()).orElseThrow();
        assertThat(excluida.getExcluidaEm()).isNotNull();
        assertThat(excluida.getEmail()).isNotEqualTo(EMAIL);
        // O e-mail fica livre para um cadastro novo
        assertThat(usuarioRepository.existsByEmail(EMAIL)).isFalse();
    }

    @Test
    void exclusaoComSenhaErradaDa400ENaoExclui() throws Exception {
        mockMvc.perform(excluir("{\"senha\":\"errada-123\"}", tokenCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erro").value("Senha incorreta"));
        mockMvc.perform(excluir("{}", tokenCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe a sua senha"));

        mockMvc.perform(get("/conta").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
        mockMvc.perform(login(SENHA)).andExpect(status().isOk());
    }

    @Test
    void exclusaoSemTokenDa401ETokenDeAdminDa403() throws Exception {
        mockMvc.perform(post("/conta/exclusao").contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(excluir("{\"senha\":\"x\"}", tokenAdmin))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder login(String senha) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"senha\":\"" + senha + "\"}");
    }

    private static MockHttpServletRequestBuilder excluir(String corpo, String token) {
        return post("/conta/exclusao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private static MockHttpServletRequestBuilder atualizar(String corpo, String token) {
        return put("/conta")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private static MockHttpServletRequestBuilder trocarSenha(String corpo, String token) {
        return put("/conta/senha")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }
}
