package com.rosie.backend.controller;

import com.rosie.backend.dto.PedidoCriadoDTO;
import com.rosie.backend.dto.PedidoRequestDTO;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;
import com.rosie.backend.service.CalculadoraFreteFalsa;
import com.rosie.backend.service.PedidoService;
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

// Modo de desenvolvimento PAGAMENTO_SIMULADO: o link de pagamento leva à loja, e a rota de simulação passa
// pelo mesmo processamento do webhook
@SpringBootTest(properties = "rosie.pagamento.simulado=true")
@AutoConfigureMockMvc
@Transactional
@Import(CalculadoraFreteFalsa.Config.class)
class PagamentoSimuladoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private CalculadoraFreteFalsa calculadora;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CarrinhoItemRepository carrinhoItemRepository;

    @Autowired
    private EntityManager entityManager;

    private Usuario cliente;
    private Produto produto;
    private PedidoCriadoDTO pedido;

    @BeforeEach
    void setUp() {
        calculadora.limpar();
        calculadora.opcoes = List.of(new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7));

        cliente = salvarCliente("cliente-simulado@rosie.test");
        Endereco endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Simulado " + System.nanoTime()).build());
        produto = Produto.builder().nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build();
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.M).quantidade(5).build());
        produtoRepository.save(produto);
        carrinhoItemRepository.save(CarrinhoItem.builder()
                .usuario(cliente).produto(produto).tamanho(Tamanho.M).quantidade(2).build());
        entityManager.flush();
        entityManager.clear();

        pedido = pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "CARTAO"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void oLinkLevaAPaginaDeSimulacaoDaLoja() {
        assertThat(pedido.urlPagamento()).isEqualTo("http://localhost:5173/pedido/" + pedido.id() + "/pagamento-simulado");
    }

    @Test
    void aprovarPelaSimulacaoBaixaOEstoque() throws Exception {
        mockMvc.perform(simular("RECUSADO", tokenService.gerar(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGAMENTO_RECUSADO"));

        mockMvc.perform(simular("APROVADO", tokenService.gerar(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.urlPagamento").doesNotExist());
        entityManager.flush();
        entityManager.clear();

        assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getEstoque().get(0).getQuantidade())
                .isEqualTo(3);
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId())).isEmpty();
    }

    @Test
    void pedidoDeOutraClienteDa404() throws Exception {
        mockMvc.perform(simular("APROVADO", tokenService.gerar(salvarCliente("outra-simulado@rosie.test"))))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder simular(String status, String token) {
        return post("/pedidos/" + pedido.id() + "/pagamento-simulado")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }

    private Usuario salvarCliente(String email) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Cliente Simulado").email(email).senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
    }
}
