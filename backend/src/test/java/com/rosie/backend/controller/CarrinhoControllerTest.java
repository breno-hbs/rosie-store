package com.rosie.backend.controller;

import com.jayway.jsonpath.JsonPath;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Rotas /carrinho contra o MySQL, com token de cliente; cada teste é desfeito no fim
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CarrinhoControllerTest {

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

    private String tokenCliente;
    private String tokenOutroCliente;
    private String tokenAdmin;
    private Produto produto;

    @BeforeEach
    void setUp() {
        tokenCliente = tokenService.gerar(salvarUsuario("cliente-rota-carrinho@rosie.test", Role.CLIENTE));
        tokenOutroCliente = tokenService.gerar(salvarUsuario("outra-rota-carrinho@rosie.test", Role.CLIENTE));
        tokenAdmin = tokenService.gerar(salvarUsuario("admin-rota-carrinho@rosie.test", Role.ADMIN));

        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Carrinho rota " + System.nanoTime()).build());
        produto = Produto.builder()
                .nome("Conjunto Serpente")
                .preco(new BigDecimal("189.00"))
                .categoria(categoria)
                .build();
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.P).quantidade(3).build());
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.M).quantidade(5).build());
        produtoRepository.save(produto);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void getDoCarrinhoVazio() throws Exception {
        mockMvc.perform(comToken(get("/carrinho"), tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0))
                .andExpect(jsonPath("$.quantidadeItens").value(0))
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(jsonPath("$.temIndisponiveis").value(false));
    }

    @Test
    void postAdiciona201ESomaNoMesmoItem() throws Exception {
        mockMvc.perform(adicionar("M", 1, tokenCliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].id").isNumber())
                .andExpect(jsonPath("$.itens[0].produto.id").value(produto.getId()))
                .andExpect(jsonPath("$.itens[0].produto.nome").value("Conjunto Serpente"))
                .andExpect(jsonPath("$.itens[0].produto.preco").value(189.00))
                .andExpect(jsonPath("$.itens[0].tamanho").value("M"))
                .andExpect(jsonPath("$.itens[0].quantidade").value(1))
                .andExpect(jsonPath("$.itens[0].disponivel").value(true))
                .andExpect(jsonPath("$.subtotal").value(189.00));
        entityManager.clear();

        mockMvc.perform(adicionar("M", 2, tokenCliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].quantidade").value(3))
                .andExpect(jsonPath("$.itens[0].subtotal").value(567.00))
                .andExpect(jsonPath("$.quantidadeItens").value(3))
                .andExpect(jsonPath("$.subtotal").value(567.00));
    }

    @Test
    void passarDoEstoqueDa409ComCodigoProprio() throws Exception {
        mockMvc.perform(adicionar("P", 4, tokenCliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTOQUE_INSUFICIENTE"))
                .andExpect(jsonPath("$.erro").value("Só temos 3 no tamanho P"));
    }

    @Test
    void tamanhoEsgotadoDa409() throws Exception {
        mockMvc.perform(adicionar("GG", 1, tokenCliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTOQUE_INSUFICIENTE"))
                .andExpect(jsonPath("$.erro").value("O tamanho GG está esgotado"));
    }

    @Test
    void produtoDesativadoDa409() throws Exception {
        produtoRepository.findById(produto.getId()).orElseThrow().setAtivo(false);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(adicionar("M", 1, tokenCliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRODUTO_INDISPONIVEL"));
    }

    @Test
    void corpoInvalidoDa400() throws Exception {
        mockMvc.perform(comToken(post("/carrinho/itens"), tokenCliente)
                        .content("{\"produtoId\":" + produto.getId() + ",\"quantidade\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erro").value("Escolha um tamanho"));
        mockMvc.perform(adicionar("XG", 1, tokenCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Tamanho inválido: use P, M, G ou GG"));
        mockMvc.perform(adicionar("M", 0, tokenCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A quantidade deve ser pelo menos 1"));
        mockMvc.perform(comToken(post("/carrinho/itens"), tokenCliente)
                        .content("{\"produtoId\":999999999,\"tamanho\":\"M\",\"quantidade\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Produto não encontrado"));
    }

    @Test
    void putMudaAQuantidadeERespeitaOEstoque() throws Exception {
        long itemId = adicionarERetornarId("M", 1);

        mockMvc.perform(comToken(put("/carrinho/itens/" + itemId), tokenCliente).content("{\"quantidade\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].quantidade").value(4))
                .andExpect(jsonPath("$.subtotal").value(756.00));
        entityManager.clear();

        mockMvc.perform(comToken(put("/carrinho/itens/" + itemId), tokenCliente).content("{\"quantidade\":6}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTOQUE_INSUFICIENTE"));
        mockMvc.perform(comToken(put("/carrinho/itens/" + itemId), tokenCliente).content("{\"quantidade\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteTiraOItemEDevolveOCarrinho() throws Exception {
        long itemId = adicionarERetornarId("M", 1);

        mockMvc.perform(comToken(delete("/carrinho/itens/" + itemId), tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0))
                .andExpect(jsonPath("$.quantidadeItens").value(0));
    }

    @Test
    void itemDeOutroClienteDa404() throws Exception {
        long itemId = adicionarERetornarId("M", 1);

        mockMvc.perform(comToken(put("/carrinho/itens/" + itemId), tokenOutroCliente).content("{\"quantidade\":2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mockMvc.perform(comToken(delete("/carrinho/itens/" + itemId), tokenOutroCliente))
                .andExpect(status().isNotFound());
        mockMvc.perform(comToken(get("/carrinho"), tokenOutroCliente))
                .andExpect(jsonPath("$.itens.length()").value(0));
    }

    @Test
    void getAvisaDeItemQueFicouIndisponivelSemApagar() throws Exception {
        adicionarERetornarId("M", 4);
        produtoRepository.findById(produto.getId()).orElseThrow().getEstoque().stream()
                .filter(estoque -> estoque.getTamanho() == Tamanho.M)
                .forEach(estoque -> estoque.setQuantidade(2));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(comToken(get("/carrinho"), tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].quantidade").value(4))
                .andExpect(jsonPath("$.itens[0].estoqueDisponivel").value(2))
                .andExpect(jsonPath("$.itens[0].disponivel").value(false))
                .andExpect(jsonPath("$.itens[0].motivoIndisponivel").value("ESTOQUE_INSUFICIENTE"))
                .andExpect(jsonPath("$.temIndisponiveis").value(true))
                .andExpect(jsonPath("$.subtotal").value(0));
    }

    @Test
    void semTokenDa401ETokenDeAdminDa403() throws Exception {
        mockMvc.perform(get("/carrinho"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(comToken(get("/carrinho"), tokenAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        mockMvc.perform(adicionar("M", 1, tokenAdmin))
                .andExpect(status().isForbidden());
    }

    private long adicionarERetornarId(String tamanho, int quantidade) throws Exception {
        String corpo = mockMvc.perform(adicionar(tamanho, quantidade, tokenCliente))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        entityManager.flush();
        entityManager.clear();
        return ((Number) JsonPath.read(corpo, "$.itens[0].id")).longValue();
    }

    private MockHttpServletRequestBuilder adicionar(String tamanho, int quantidade, String token) {
        return comToken(post("/carrinho/itens"), token).content(
                "{\"produtoId\":" + produto.getId() + ",\"tamanho\":\"" + tamanho + "\",\"quantidade\":" + quantidade + "}");
    }

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder requisicao, String token) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private Usuario salvarUsuario(String email, Role role) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuário Carrinho")
                .email(email)
                .senhaHash("hash-irrelevante")
                .role(role)
                .build());
    }
}
