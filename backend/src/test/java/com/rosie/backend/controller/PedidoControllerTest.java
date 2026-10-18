package com.rosie.backend.controller;

import com.rosie.backend.exception.PagamentoFalhouException;
import com.rosie.backend.exception.PagamentoIndisponivelException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.model.Pedido;
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
import com.rosie.backend.service.GatewayPagamentoFalso;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /pedidos, GET /pedidos e GET /pedidos/{id} contra o MySQL, com token de cliente, frete e Mercado Pago falsos
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({CalculadoraFreteFalsa.Config.class, GatewayPagamentoFalso.Config.class})
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CalculadoraFreteFalsa calculadora;

    @Autowired
    private GatewayPagamentoFalso gateway;

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
    private PedidoRepository pedidoRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    private Usuario cliente;
    private String tokenCliente;
    private String tokenOutraCliente;
    private String tokenAdmin;
    private Endereco endereco;
    private Produto produto;

    @BeforeEach
    void setUp() {
        calculadora.limpar();
        gateway.limpar();
        calculadora.opcoes = List.of(new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7));

        cliente = salvarUsuario("cliente-rota-pedido@rosie.test", Role.CLIENTE);
        tokenCliente = tokenService.gerar(cliente);
        tokenOutraCliente = tokenService.gerar(salvarUsuario("outra-rota-pedido@rosie.test", Role.CLIENTE));
        tokenAdmin = tokenService.gerar(salvarUsuario("admin-rota-pedido@rosie.test", Role.ADMIN));
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());

        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Pedido rota " + System.nanoTime()).build());
        produto = Produto.builder()
                .nome("Conjunto Serpente")
                .preco(new BigDecimal("189.00"))
                .categoria(categoria)
                .build();
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.M).quantidade(1).build());
        produtoRepository.save(produto);
    }

    @Test
    void criaOPedidoEDevolveOLinkDePagamento() throws Exception {
        adicionarAoCarrinho(1);

        String resposta = mockMvc.perform(criar(corpo(endereco.getId(), 1, "CARTAO"), tokenCliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.subtotal").value(189.00))
                .andExpect(jsonPath("$.valorFrete").value(22.90))
                .andExpect(jsonPath("$.valorTotal").value(211.90))
                .andExpect(jsonPath("$.urlPagamento").value(startsWith("https://www.mercadopago.com.br/")))
                .andReturn().getResponse().getContentAsString();
        long id = jsonMapper.readTree(resposta).get("id").asLong();

        mockMvc.perform(get("/pedidos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.formaPagamento").value("CARTAO"))
                .andExpect(jsonPath("$.servicoFrete").value("PAC"))
                .andExpect(jsonPath("$.transportadora").value("Correios"))
                .andExpect(jsonPath("$.prazoFreteDias").value(7))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua das Flores"))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Conjunto Serpente"))
                .andExpect(jsonPath("$.itens[0].tamanho").value("M"))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(189.00))
                .andExpect(jsonPath("$.urlPagamento").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Pedido de outra cliente é invisível
        mockMvc.perform(get("/pedidos/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutraCliente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void estoqueInsuficienteDa409() throws Exception {
        adicionarAoCarrinho(2);

        mockMvc.perform(criar(corpo(endereco.getId(), 1, "PIX"), tokenCliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTOQUE_INSUFICIENTE"))
                .andExpect(jsonPath("$.erro").value("Só temos 1 de Conjunto Serpente (M). Ajuste a quantidade na sacola."));
        assertThat(gateway.preferencias).isEmpty();
    }

    @Test
    void corpoInvalidoDa400() throws Exception {
        adicionarAoCarrinho(1);
        for (String corpo : List.of(
                corpo(endereco.getId(), 1, "BOLETO"),
                "{\"servicoFreteId\":1,\"formaPagamento\":\"PIX\"}",
                "{\"enderecoId\":" + endereco.getId() + ",\"formaPagamento\":\"PIX\"}",
                "{}")) {
            mockMvc.perform(criar(corpo, tokenCliente))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        }
        // Endereço da outra cliente
        mockMvc.perform(criar(corpo(endereco.getId(), 1, "PIX"), tokenOutraCliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Endereço de entrega não encontrado"));
    }

    @Test
    void mercadoPagoSemConfiguracaoDa503EForaDoArDa502() throws Exception {
        adicionarAoCarrinho(1);

        gateway.falha = new PagamentoIndisponivelException("O pagamento não está configurado no servidor");
        mockMvc.perform(criar(corpo(endereco.getId(), 1, "PIX"), tokenCliente))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PAGAMENTO_INDISPONIVEL"));

        gateway.falha = new PagamentoFalhouException("Não foi possível iniciar o pagamento agora. Tente de novo em instantes.");
        mockMvc.perform(criar(corpo(endereco.getId(), 1, "PIX"), tokenCliente))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("PAGAMENTO_FALHOU"));
    }

    @Test
    void semTokenDa401ETokenDeAdminDa403() throws Exception {
        mockMvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(corpo(endereco.getId(), 1, "PIX")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(criar(corpo(endereco.getId(), 1, "PIX"), tokenAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    void listaOsPedidosDaClienteDoMaisRecenteParaOMaisAntigo() throws Exception {
        Pedido primeiro = salvarPedido(StatusPedido.ENTREGUE, "211.90");
        Pedido segundo = salvarPedido(StatusPedido.ENVIADO, "189.00");
        segundo.setCodigoRastreio("AA123456789BR");
        entityManager.flush();

        // Criados em sequência: o mais novo vem primeiro (no mesmo segundo, o id desempata)
        mockMvc.perform(get("/pedidos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(segundo.getId()))
                .andExpect(jsonPath("$[0].status").value("ENVIADO"))
                .andExpect(jsonPath("$[0].valorTotal").value(189.00))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(primeiro.getId()))
                .andExpect(jsonPath("$[1].status").value("ENTREGUE"));

        mockMvc.perform(get("/pedidos/" + segundo.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoRastreio").value("AA123456789BR"))
                .andExpect(jsonPath("$.urlPagamento").doesNotExist());

        // A outra cliente não vê nenhum, nem pelo id
        mockMvc.perform(get("/pedidos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutraCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/pedidos/" + segundo.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutraCliente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void listaSemTokenDa401EComTokenDeAdminDa403() throws Exception {
        mockMvc.perform(get("/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mockMvc.perform(get("/pedidos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    private Pedido salvarPedido(StatusPedido status, String total) {
        return pedidoRepository.saveAndFlush(Pedido.builder()
                .usuario(cliente)
                .endereco(endereco)
                .status(status)
                .formaPagamento(FormaPagamento.CARTAO)
                .transportadora("Correios")
                .servicoFrete("PAC")
                .prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90"))
                .valorTotal(new BigDecimal(total))
                .build());
    }

    private void adicionarAoCarrinho(int quantidade) {
        carrinhoItemRepository.save(CarrinhoItem.builder()
                .usuario(cliente).produto(produto).tamanho(Tamanho.M).quantidade(quantidade).build());
        entityManager.flush();
        entityManager.clear();
    }

    private static String corpo(Long enderecoId, int servicoFreteId, String formaPagamento) {
        return "{\"enderecoId\":" + enderecoId + ",\"servicoFreteId\":" + servicoFreteId
                + ",\"formaPagamento\":\"" + formaPagamento + "\"}";
    }

    private static MockHttpServletRequestBuilder criar(String corpo, String token) {
        return post("/pedidos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private Usuario salvarUsuario(String email, Role role) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuário Pedido")
                .email(email)
                .senhaHash("hash-irrelevante")
                .role(role)
                .build());
    }
}
