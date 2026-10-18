package com.rosie.backend.controller;

import com.rosie.backend.dto.PedidoCriadoDTO;
import com.rosie.backend.dto.PedidoRequestDTO;
import com.rosie.backend.exception.PagamentoFalhouException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;
import com.rosie.backend.service.CalculadoraFreteFalsa;
import com.rosie.backend.service.GatewayPagamento.StatusPagamento;
import com.rosie.backend.service.GatewayPagamentoFalso;
import com.rosie.backend.service.PedidoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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

// POST /pagamentos/webhook como o Mercado Pago manda: sem token, com x-signature. Mercado Pago falso.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({CalculadoraFreteFalsa.Config.class, GatewayPagamentoFalso.Config.class})
class PagamentoWebhookControllerTest {

    // Corpo real de um aviso de pagamento (o webhook só usa os parâmetros da URL)
    private static final String CORPO = """
            { "action": "payment.updated", "api_version": "v1", "data": { "id": "pg-1" },
              "date_created": "2026-10-18T10:04:58Z", "id": 123456, "live_mode": false, "type": "payment",
              "user_id": "4321" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private CalculadoraFreteFalsa calculadora;

    @Autowired
    private GatewayPagamentoFalso gateway;

    @Autowired
    private PedidoRepository pedidoRepository;

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

    private Produto produto;
    private PedidoCriadoDTO pedido;

    @BeforeEach
    void setUp() {
        calculadora.limpar();
        gateway.limpar();
        calculadora.opcoes = List.of(new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7));

        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Webhook").email("cliente-webhook@rosie.test").senhaHash("hash-irrelevante")
                .role(Role.CLIENTE).build());
        Endereco endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Webhook " + System.nanoTime()).build());
        produto = Produto.builder().nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build();
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.M).quantidade(5).build());
        produtoRepository.save(produto);
        carrinhoItemRepository.save(CarrinhoItem.builder()
                .usuario(cliente).produto(produto).tamanho(Tamanho.M).quantidade(1).build());
        entityManager.flush();
        entityManager.clear();

        pedido = pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "PIX"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void pagamentoAprovadoBaixaOEstoqueUmaVezMesmoRecebendoDuasVezes() throws Exception {
        gateway.programar("pg-1", StatusPagamento.APROVADO, pedido.id(), new BigDecimal("211.90"));

        mockMvc.perform(aviso("payment", "pg-1", GatewayPagamentoFalso.ASSINATURA_VALIDA)).andExpect(status().isOk());
        mockMvc.perform(aviso("payment", "pg-1", GatewayPagamentoFalso.ASSINATURA_VALIDA)).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(estoqueM()).isEqualTo(4);
        assertThat(gateway.consultas).containsExactly("pg-1", "pg-1");
    }

    @Test
    void assinaturaInvalidaDa401SemConsultarNemMexer() throws Exception {
        gateway.programar("pg-1", StatusPagamento.APROVADO, pedido.id(), new BigDecimal("211.90"));

        mockMvc.perform(aviso("payment", "pg-1", "ts=1700000000,v1=forjada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ASSINATURA_INVALIDA"));
        mockMvc.perform(aviso("payment", "pg-1", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ASSINATURA_INVALIDA"));
        entityManager.flush();
        entityManager.clear();

        assertThat(gateway.consultas).isEmpty();
        assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(estoqueM()).isEqualTo(5);
    }

    @Test
    void pagamentoRecusadoMudaOStatusSemMexerNoEstoque() throws Exception {
        gateway.programar("pg-1", StatusPagamento.RECUSADO, pedido.id(), new BigDecimal("211.90"));

        mockMvc.perform(aviso("payment", "pg-1", GatewayPagamentoFalso.ASSINATURA_VALIDA)).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.PAGAMENTO_RECUSADO);
        assertThat(estoqueM()).isEqualTo(5);
    }

    @Test
    void outrosAvisosSaoConfirmadosSemProcessar() throws Exception {
        // merchant_order e o formato antigo (?topic=payment&id=...) não vêm assinados
        mockMvc.perform(aviso("merchant_order", "777", null)).andExpect(status().isOk());
        mockMvc.perform(post("/pagamentos/webhook").param("topic", "payment").param("id", "pg-1"))
                .andExpect(status().isOk());
        assertThat(gateway.consultas).isEmpty();
    }

    @Test
    void mercadoPagoForaDoArNaConsultaDa502ParaEleTentarDeNovo() throws Exception {
        gateway.falha = new PagamentoFalhouException("Não foi possível consultar o pagamento");

        mockMvc.perform(aviso("payment", "pg-1", GatewayPagamentoFalso.ASSINATURA_VALIDA))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("PAGAMENTO_FALHOU"));
    }

    private int estoqueM() {
        return produtoRepository.findById(produto.getId()).orElseThrow().getEstoque().get(0).getQuantidade();
    }

    private static MockHttpServletRequestBuilder aviso(String tipo, String dataId, String assinatura) {
        MockHttpServletRequestBuilder requisicao = post("/pagamentos/webhook")
                .param("data.id", dataId)
                .param("type", tipo)
                .header("x-request-id", "2066ca19-c6f1-498a-be75-1923005edd06")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO);
        return assinatura == null ? requisicao : requisicao.header("x-signature", assinatura);
    }
}
