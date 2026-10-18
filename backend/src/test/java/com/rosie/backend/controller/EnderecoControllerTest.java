package com.rosie.backend.controller;

import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /enderecos contra o MySQL, com token de cliente; desfaz tudo ao fim de cada teste
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnderecoControllerTest {

    private static final String CORPO = """
            { "cep": "03925-050", "logradouro": " Rua das Flores ", "numero": "120", "complemento": "",
              "bairro": "Jardim Iva", "cidade": "São Paulo", "estado": "sp" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EntityManager entityManager;

    private Usuario cliente;
    private String tokenCliente;
    private String tokenOutraCliente;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        cliente = salvarUsuario("cliente-enderecos@rosie.test", Role.CLIENTE);
        tokenCliente = tokenService.gerar(cliente);
        tokenOutraCliente = tokenService.gerar(salvarUsuario("outra-enderecos@rosie.test", Role.CLIENTE));
        tokenAdmin = tokenService.gerar(salvarUsuario("admin-enderecos@rosie.test", Role.ADMIN));
    }

    @Test
    void criaEListaOsEnderecosDaCliente() throws Exception {
        mockMvc.perform(com(post("/enderecos"), CORPO, tokenCliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.cep").value("03925050"))
                .andExpect(jsonPath("$.logradouro").value("Rua das Flores"))
                .andExpect(jsonPath("$.complemento").doesNotExist())
                .andExpect(jsonPath("$.estado").value("SP"));

        mockMvc.perform(get("/enderecos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cidade").value("São Paulo"));

        // A outra cliente não vê
        mockMvc.perform(get("/enderecos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutraCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void camposInvalidosDao400() throws Exception {
        for (String corpo : List.of(
                CORPO.replace("03925-050", "0392505"),
                CORPO.replace("\"sp\"", "\"XX\""),
                CORPO.replace("\"120\"", "\"\""),
                CORPO.replace("\"Jardim Iva\"", "\"  \""),
                "{}")) {
            mockMvc.perform(com(post("/enderecos"), corpo, tokenCliente))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        }
        assertThat(enderecoRepository.findByUsuarioIdAndArquivadoFalseOrderByIdAsc(cliente.getId())).isEmpty();
    }

    @Test
    void atualizaERemoveEnderecoSemPedido() throws Exception {
        Endereco endereco = salvarEndereco(cliente);

        mockMvc.perform(com(put("/enderecos/" + endereco.getId()), CORPO.replace("\"120\"", "\"99\""), tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(endereco.getId()))
                .andExpect(jsonPath("$.numero").value("99"));

        mockMvc.perform(delete("/enderecos/" + endereco.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(enderecoRepository.findById(endereco.getId())).isEmpty();
    }

    @Test
    void enderecoUsadoEmPedidoEArquivadoEmVezDeMudar() throws Exception {
        Endereco endereco = salvarEndereco(cliente);
        pedidoRepository.save(Pedido.builder()
                .usuario(cliente)
                .endereco(endereco)
                .valorTotal(new BigDecimal("100.00"))
                .build());
        entityManager.flush();

        // Editar cria outro endereço e tira o antigo da lista; o pedido continua com o original
        mockMvc.perform(com(put("/enderecos/" + endereco.getId()), CORPO.replace("\"120\"", "\"99\""), tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("99"))
                .andExpect(jsonPath("$.id").value(not(endereco.getId().intValue())));
        entityManager.flush();
        entityManager.clear();

        Endereco original = enderecoRepository.findById(endereco.getId()).orElseThrow();
        assertThat(original.getArquivado()).isTrue();
        assertThat(original.getNumero()).isEqualTo("10");
        assertThat(enderecoRepository.findByUsuarioIdAndArquivadoFalseOrderByIdAsc(cliente.getId()))
                .extracting(Endereco::getNumero).containsExactly("99");

        // O arquivado não aparece mais nas rotas
        mockMvc.perform(delete("/enderecos/" + endereco.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isNotFound());
    }

    @Test
    void enderecoDeOutraClienteDa404() throws Exception {
        Endereco endereco = salvarEndereco(cliente);

        mockMvc.perform(com(put("/enderecos/" + endereco.getId()), CORPO, tokenOutraCliente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mockMvc.perform(delete("/enderecos/" + endereco.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutraCliente))
                .andExpect(status().isNotFound());
        assertThat(enderecoRepository.findById(endereco.getId())).isPresent();
    }

    @Test
    void semTokenDa401ETokenDeAdminDa403() throws Exception {
        mockMvc.perform(get("/enderecos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(get("/enderecos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    private Endereco salvarEndereco(Usuario dona) {
        Endereco endereco = enderecoRepository.save(Endereco.builder()
                .usuario(dona)
                .cep("03925050")
                .logradouro("Rua das Flores")
                .numero("10")
                .bairro("Jardim Iva")
                .cidade("São Paulo")
                .estado("SP")
                .build());
        entityManager.flush();
        return endereco;
    }

    private static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder requisicao, String corpo, String token) {
        return requisicao
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private Usuario salvarUsuario(String email, Role role) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuário Endereços")
                .email(email)
                .senhaHash("hash-irrelevante")
                .role(role)
                .build());
    }
}
