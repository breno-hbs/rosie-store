package com.rosie.backend.controller;

import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// PUT /admin/produtos/{id}/estoque contra o MySQL, com token de admin; cada teste é desfeito no fim
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProdutoEstoqueAdminControllerTest {

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

    @Autowired
    private EntityManager entityManager;

    private String tokenAdmin;
    private String tokenCliente;
    private Categoria categoria;
    private Produto produto;

    @BeforeEach
    void setUp() {
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste")
                .email("admin-estoque@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.ADMIN)
                .build());
        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Teste")
                .email("cliente-estoque@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());
        tokenAdmin = tokenService.gerar(admin);
        tokenCliente = tokenService.gerar(cliente);

        categoria = categoriaRepository.save(Categoria.builder().nome("Vestido estoque " + System.nanoTime()).build());
        produto = Produto.builder()
                .nome("Conjunto Serpente")
                .preco(new BigDecimal("189.00"))
                .categoria(categoria)
                .build();
        produto.getEstoque().add(linha(Tamanho.P, 3));
        produto.getEstoque().add(linha(Tamanho.M, 5));
        produtoRepository.save(produto);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void atualizaSoOsTamanhosEnviadosERespondeOEstoqueCompleto() throws Exception {
        mockMvc.perform(comAdmin(put(rota(produto.getId())))
                        .content("{\"estoque\":[{\"tamanho\":\"GG\",\"quantidade\":7},{\"tamanho\":\"M\",\"quantidade\":0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtoId").value(produto.getId()))
                .andExpect(jsonPath("$.estoque.length()").value(3))
                .andExpect(jsonPath("$.estoque[0].tamanho").value("P"))
                .andExpect(jsonPath("$.estoque[0].quantidade").value(3))
                .andExpect(jsonPath("$.estoque[1].tamanho").value("M"))
                .andExpect(jsonPath("$.estoque[1].quantidade").value(0))
                .andExpect(jsonPath("$.estoque[2].tamanho").value("GG"))
                .andExpect(jsonPath("$.estoque[2].quantidade").value(7));

        // a lista do painel traz o estoque por tamanho, que a tela de Gestão de Estoque usa
        entityManager.clear();
        mockMvc.perform(comAdmin(get("/admin/produtos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + produto.getId() + ")].estoqueTotal").value(10))
                .andExpect(jsonPath("$[?(@.id == " + produto.getId() + ")].estoque[2].tamanho").value("GG"));
    }

    @Test
    void zerarTodosOsTamanhosDeixaOProdutoIndisponivelNaVitrine() throws Exception {
        mockMvc.perform(get("/produtos").param("categoriaId", categoria.getId().toString()))
                .andExpect(jsonPath("$.conteudo[0].id").value(produto.getId()))
                .andExpect(jsonPath("$.conteudo[0].disponivel").value(true));

        mockMvc.perform(comAdmin(put(rota(produto.getId())))
                        .content("{\"estoque\":[{\"tamanho\":\"P\",\"quantidade\":0},{\"tamanho\":\"M\",\"quantidade\":0}]}"))
                .andExpect(status().isOk());
        entityManager.clear();

        mockMvc.perform(get("/produtos").param("categoriaId", categoria.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].id").value(produto.getId()))
                .andExpect(jsonPath("$.conteudo[0].disponivel").value(false));
    }

    @Test
    void quantidadeNegativaRetorna400() throws Exception {
        esperarDadosInvalidos("{\"estoque\":[{\"tamanho\":\"P\",\"quantidade\":-1}]}",
                "A quantidade não pode ser negativa");
    }

    @Test
    void tamanhoForaDaGradeRetorna400() throws Exception {
        esperarDadosInvalidos("{\"estoque\":[{\"tamanho\":\"XG\",\"quantidade\":1}]}",
                "Tamanho inválido: use P, M, G ou GG");
    }

    @Test
    void tamanhoRepetidoRetorna400() throws Exception {
        esperarDadosInvalidos("{\"estoque\":[{\"tamanho\":\"P\",\"quantidade\":1},{\"tamanho\":\"P\",\"quantidade\":2}]}",
                "Tamanho P repetido no estoque");
    }

    @Test
    void estoqueVazioRetorna400() throws Exception {
        esperarDadosInvalidos("{\"estoque\":[]}", "Informe ao menos um tamanho");
        esperarDadosInvalidos("{}", "Informe ao menos um tamanho");
    }

    @Test
    void produtoInexistenteRetorna404() throws Exception {
        mockMvc.perform(comAdmin(put(rota(-1L))).content("{\"estoque\":[{\"tamanho\":\"P\",\"quantidade\":1}]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(put(rota(produto.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estoque\":[{\"tamanho\":\"P\",\"quantidade\":1}]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void tokenDeClienteRetorna403() throws Exception {
        mockMvc.perform(put(rota(produto.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estoque\":[{\"tamanho\":\"P\",\"quantidade\":1}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    private void esperarDadosInvalidos(String corpo, String mensagem) throws Exception {
        mockMvc.perform(comAdmin(put(rota(produto.getId()))).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(mensagem))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    private MockHttpServletRequestBuilder comAdmin(MockHttpServletRequestBuilder requisicao) {
        return requisicao
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String rota(Long id) {
        return "/admin/produtos/" + id + "/estoque";
    }

    private ProdutoEstoque linha(Tamanho tamanho, int quantidade) {
        return ProdutoEstoque.builder().produto(produto).tamanho(tamanho).quantidade(quantidade).build();
    }
}
