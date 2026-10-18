package com.rosie.backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CRUD do painel contra o MySQL, com token de admin; cada teste roda numa transação desfeita no fim
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProdutoAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TokenService tokenService;

    private String tokenAdmin;
    private String tokenCliente;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email("admin-produtos@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.ADMIN)
                .build());
        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Teste")
                .email("cliente-produtos@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());
        tokenAdmin = tokenService.gerar(admin);
        tokenCliente = tokenService.gerar(cliente);
        categoria = categoriaRepository.save(Categoria.builder().nome("Vestido teste " + System.nanoTime()).build());
    }

    @Test
    void criarRetorna201ComProdutoNoFormatoDoDetalhe() throws Exception {
        mockMvc.perform(comAdmin(post("/admin/produtos")).content(corpo("Conjunto Serpente", "189.00", categoria.getId(),
                        "[{\"tamanho\":\"M\",\"quantidade\":5},{\"tamanho\":\"P\",\"quantidade\":3}]", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Conjunto Serpente"))
                .andExpect(jsonPath("$.preco").value(189.00))
                .andExpect(jsonPath("$.categoria.id").value(categoria.getId()))
                .andExpect(jsonPath("$.imagens").isEmpty())
                .andExpect(jsonPath("$.estoque[0].tamanho").value("P"))
                .andExpect(jsonPath("$.estoque[1].quantidade").value(5))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.destaque").value(true));
    }

    @Test
    void fluxoCompletoEditaDesativaEReativa() throws Exception {
        String criado = mockMvc.perform(comAdmin(post("/admin/produtos"))
                        .content(corpo("Original", "100.00", categoria.getId(), "[]", false)))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(criado, "$.id");

        mockMvc.perform(comAdmin(put("/admin/produtos/" + id))
                        .content(corpo("Editado", "120.00", categoria.getId(), "[{\"tamanho\":\"GG\",\"quantidade\":2}]", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Editado"))
                .andExpect(jsonPath("$.destaque").value(true))
                .andExpect(jsonPath("$.estoque[0].tamanho").value("GG"));

        mockMvc.perform(comAdmin(delete("/admin/produtos/" + id)))
                .andExpect(status().isNoContent());

        // some da vitrine, mas continua no painel como inativo
        mockMvc.perform(get("/produtos/" + id))
                .andExpect(status().isNotFound());
        mockMvc.perform(comAdmin(get("/admin/produtos/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(comAdmin(get("/admin/produtos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].ativo").value(false))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].estoqueTotal").value(2))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].categoria.nome").value(categoria.getNome()));

        mockMvc.perform(comAdmin(put("/admin/produtos/" + id)).content("""
                        {"nome":"Editado","preco":120.00,"categoriaId":%d,"ativo":true,
                         "estoque":[{"tamanho":"GG","quantidade":2}]}
                        """.formatted(categoria.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.destaque").value(true));
        mockMvc.perform(get("/produtos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").doesNotExist())
                .andExpect(jsonPath("$.destaque").doesNotExist());
    }

    @Test
    void nomeVazioRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("  ", "100.00", categoria.getId(), "[]", false), "Informe o nome do produto");
    }

    @Test
    void precoZeroRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "0", categoria.getId(), "[]", false), "O preço deve ser maior que zero");
    }

    @Test
    void categoriaInexistenteRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "100.00", -1L, "[]", false), "Categoria não encontrada");
    }

    @Test
    void tamanhoForaDaGradeRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "100.00", categoria.getId(), "[{\"tamanho\":\"XG\",\"quantidade\":1}]", false),
                "Tamanho inválido: use P, M, G ou GG");
    }

    @Test
    void tamanhoRepetidoRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "100.00", categoria.getId(),
                "[{\"tamanho\":\"P\",\"quantidade\":1},{\"tamanho\":\"P\",\"quantidade\":2}]", false),
                "Tamanho P repetido no estoque");
    }

    @Test
    void quantidadeNegativaRetorna400() throws Exception {
        esperarDadosInvalidos(corpo("Produto", "100.00", categoria.getId(), "[{\"tamanho\":\"P\",\"quantidade\":-1}]", false),
                "A quantidade não pode ser negativa");
    }

    @Test
    void produtoInexistenteRetorna404() throws Exception {
        mockMvc.perform(comAdmin(put("/admin/produtos/-1")).content(corpo("Produto", "100.00", categoria.getId(), "[]", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mockMvc.perform(comAdmin(delete("/admin/produtos/-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/admin/produtos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void tokenDeClienteRetorna403() throws Exception {
        mockMvc.perform(post("/admin/produtos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Produto", "100.00", categoria.getId(), "[]", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    private void esperarDadosInvalidos(String corpo, String mensagem) throws Exception {
        mockMvc.perform(comAdmin(post("/admin/produtos")).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(mensagem))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    private MockHttpServletRequestBuilder comAdmin(MockHttpServletRequestBuilder requisicao) {
        return requisicao
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String corpo(String nome, String preco, Long categoriaId, String estoque, boolean destaque) {
        return """
                {"nome":"%s","descricao":"Tecido leve","preco":%s,"categoriaId":%d,"destaque":%b,"estoque":%s}
                """.formatted(nome, preco, categoriaId, destaque, estoque);
    }
}
