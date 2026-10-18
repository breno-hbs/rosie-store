package com.rosie.backend.controller;

import com.rosie.backend.exception.FreteFalhouException;
import com.rosie.backend.exception.FreteIndisponivelException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;
import com.rosie.backend.service.CalculadoraFreteFalsa;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /frete/calcular contra o MySQL, com token de cliente e o Melhor Envio falso
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CalculadoraFreteFalsa.Config.class)
class FreteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CalculadoraFreteFalsa calculadora;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CarrinhoItemRepository carrinhoItemRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EntityManager entityManager;

    private Usuario cliente;
    private String tokenCliente;
    private String tokenAdmin;
    private Produto produto;

    @BeforeEach
    void setUp() {
        calculadora.limpar();
        cliente = salvarUsuario("cliente-rota-frete@rosie.test", Role.CLIENTE);
        tokenCliente = tokenService.gerar(cliente);
        tokenAdmin = tokenService.gerar(salvarUsuario("admin-rota-frete@rosie.test", Role.ADMIN));

        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Frete rota " + System.nanoTime()).build());
        produto = Produto.builder()
                .nome("Conjunto Serpente")
                .preco(new BigDecimal("189.00"))
                .categoria(categoria)
                .build();
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.M).quantidade(5).build());
        produtoRepository.save(produto);
    }

    @Test
    void calculaAsOpcoesDoCarrinho() throws Exception {
        adicionarAoCarrinho();
        calculadora.opcoes = List.of(
                new OpcaoFrete(2, "Correios", "SEDEX", new BigDecimal("38.50"), 3),
                new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7));

        mockMvc.perform(calcular("{\"cep\":\"03925-050\"}", tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("03925050"))
                .andExpect(jsonPath("$.opcoes.length()").value(2))
                .andExpect(jsonPath("$.opcoes[0].id").value(1))
                .andExpect(jsonPath("$.opcoes[0].transportadora").value("Correios"))
                .andExpect(jsonPath("$.opcoes[0].servico").value("PAC"))
                .andExpect(jsonPath("$.opcoes[0].preco").value(22.90))
                .andExpect(jsonPath("$.opcoes[0].prazoDias").value(7))
                .andExpect(jsonPath("$.opcoes[1].servico").value("SEDEX"));
    }

    @Test
    void cepInvalidoDa400SemChamarOMelhorEnvio() throws Exception {
        adicionarAoCarrinho();
        for (String corpo : List.of("{\"cep\":\"0392505\"}", "{\"cep\":\"03925-0500\"}", "{\"cep\":\"abcde-fgh\"}",
                "{\"cep\":\"\"}", "{}")) {
            mockMvc.perform(calcular(corpo, tokenCliente))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        }
        assertThat(calculadora.chamadas).isEmpty();
    }

    @Test
    void carrinhoVazioDa409() throws Exception {
        mockMvc.perform(calcular("{\"cep\":\"03925050\"}", tokenCliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CARRINHO_VAZIO"));
    }

    @Test
    void semConfiguracaoDa503EFalhaDoMelhorEnvioDa502() throws Exception {
        adicionarAoCarrinho();

        calculadora.falha = new FreteIndisponivelException("O cálculo de frete não está configurado no servidor");
        mockMvc.perform(calcular("{\"cep\":\"03925050\"}", tokenCliente))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("FRETE_INDISPONIVEL"));

        calculadora.falha = new FreteFalhouException("Não foi possível calcular o frete agora. Tente de novo em instantes.");
        mockMvc.perform(calcular("{\"cep\":\"03925050\"}", tokenCliente))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("FRETE_FALHOU"))
                .andExpect(jsonPath("$.erro").value("Não foi possível calcular o frete agora. Tente de novo em instantes."));

        // O carrinho continua intacto
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId())).hasSize(1);
    }

    @Test
    void semTokenDa401ETokenDeAdminDa403() throws Exception {
        mockMvc.perform(post("/frete/calcular").contentType(MediaType.APPLICATION_JSON).content("{\"cep\":\"03925050\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(calcular("{\"cep\":\"03925050\"}", tokenAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    private void adicionarAoCarrinho() {
        carrinhoItemRepository.save(CarrinhoItem.builder()
                .usuario(cliente)
                .produto(produto)
                .tamanho(Tamanho.M)
                .quantidade(1)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    private static MockHttpServletRequestBuilder calcular(String corpo, String token) {
        return post("/frete/calcular")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private Usuario salvarUsuario(String email, Role role) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuário Frete")
                .email(email)
                .senhaHash("hash-irrelevante")
                .role(role)
                .build());
    }
}
