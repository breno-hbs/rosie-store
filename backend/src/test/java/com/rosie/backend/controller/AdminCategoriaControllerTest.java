package com.rosie.backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.TokenService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fluxo completo contra o MySQL, com token de admin: cada teste roda numa transação desfeita no fim
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCategoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private TokenService tokenService;

    private String tokenAdmin;
    private String tokenCliente;
    private String sufixo;

    @BeforeEach
    void setUp() {
        tokenAdmin = tokenService.gerar(usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email("admin-categorias@rosie.test")
                .senhaHash("nao-usada")
                .role(Role.ADMIN)
                .build()));
        tokenCliente = tokenService.gerar(usuarioRepository.save(Usuario.builder()
                .nome("Cliente Teste")
                .email("cliente-categorias@rosie.test")
                .senhaHash("nao-usada")
                .role(Role.CLIENTE)
                .build()));
        sufixo = String.valueOf(System.nanoTime());
    }

    @Test
    void listarTrazIdNomeEQuantidadeDeProdutos() throws Exception {
        Categoria categoria = novaCategoria("Listada " + sufixo);
        novoProduto(categoria, true);
        novoProduto(categoria, false);

        mockMvc.perform(comoAdmin(get("/admin/categorias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].nome".formatted(categoria.getId())).value("Listada " + sufixo))
                .andExpect(jsonPath("$[?(@.id == %d)].quantidadeProdutos".formatted(categoria.getId())).value(2));
    }

    @Test
    void criarRetorna201ComACategoriaSemEspacosNasPontas() throws Exception {
        String corpo = mockMvc.perform(comoAdmin(post("/admin/categorias")).content(nome("  Acessórios " + sufixo + " ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Acessórios " + sufixo))
                .andExpect(jsonPath("$.quantidadeProdutos").value(0))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(corpo, "$.id")).longValue();
        assertThat(categoriaRepository.findById(id)).isPresent();
    }

    @Test
    void criarComNomeDuplicadoRetorna409() throws Exception {
        novaCategoria("Bolsas " + sufixo);

        mockMvc.perform(comoAdmin(post("/admin/categorias")).content(nome("bolsas " + sufixo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Já existe uma categoria com esse nome"))
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"));
    }

    @Test
    void criarSemNomeRetorna400() throws Exception {
        mockMvc.perform(comoAdmin(post("/admin/categorias")).content(nome("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe o nome da categoria"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void criarComNomeLongoDemaisRetorna400() throws Exception {
        mockMvc.perform(comoAdmin(post("/admin/categorias")).content(nome("x".repeat(81))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void renomearRetorna200ComONovoNome() throws Exception {
        Categoria categoria = novaCategoria("Antigo " + sufixo);

        mockMvc.perform(comoAdmin(put("/admin/categorias/" + categoria.getId())).content(nome("Novo " + sufixo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoria.getId()))
                .andExpect(jsonPath("$.nome").value("Novo " + sufixo))
                .andExpect(jsonPath("$.quantidadeProdutos").value(0));
    }

    @Test
    void renomearCategoriaInexistenteRetorna404() throws Exception {
        mockMvc.perform(comoAdmin(put("/admin/categorias/-1")).content(nome("Novo " + sufixo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void removerCategoriaVaziaRetorna204() throws Exception {
        Categoria categoria = novaCategoria("Vazia " + sufixo);

        mockMvc.perform(comoAdmin(delete("/admin/categorias/" + categoria.getId())))
                .andExpect(status().isNoContent());

        assertThat(categoriaRepository.findById(categoria.getId())).isEmpty();
    }

    @Test
    void removerCategoriaComProdutosRetorna409() throws Exception {
        Categoria categoria = novaCategoria("Em uso " + sufixo);
        novoProduto(categoria, true);
        novoProduto(categoria, false);

        mockMvc.perform(comoAdmin(delete("/admin/categorias/" + categoria.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Não dá para remover \"Em uso " + sufixo
                        + "\": a categoria tem 2 produtos. Mova ou exclua os produtos antes."))
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_EM_USO"));
    }

    @Test
    void removerCategoriaInexistenteRetorna404() throws Exception {
        mockMvc.perform(comoAdmin(delete("/admin/categorias/-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Categoria não encontrada"))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/admin/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void tokenDeClienteRetorna403() throws Exception {
        mockMvc.perform(post("/admin/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nome("Proibida " + sufixo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    private MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder requisicao) {
        return requisicao
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static String nome(String nome) {
        return "{\"nome\": \"%s\"}".formatted(nome);
    }

    private Categoria novaCategoria(String nome) {
        return categoriaRepository.save(Categoria.builder().nome(nome).build());
    }

    private void novoProduto(Categoria categoria, boolean ativo) {
        produtoRepository.save(Produto.builder()
                .categoria(categoria)
                .nome("Produto " + (ativo ? "ativo" : "inativo"))
                .preco(new BigDecimal("100.00"))
                .ativo(ativo)
                .build());
    }
}
