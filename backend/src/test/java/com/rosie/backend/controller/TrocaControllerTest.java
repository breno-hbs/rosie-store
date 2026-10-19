package com.rosie.backend.controller;

import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.PrazoTroca;
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
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST/GET /trocas, GET /admin/trocas, PUT /admin/trocas/{id}/status e POST /admin/pedidos/{id}/entregue
// (#27) contra o MySQL
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrocaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PrazoTroca prazoTroca;

    private String tokenAdmin;
    private String tokenCliente;
    private String tokenOutraCliente;
    private Usuario cliente;
    private Endereco endereco;
    private Produto produto;

    @BeforeEach
    void setUp() {
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste").email("admin-trocas@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.ADMIN).build());
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Cliente").email("cliente-trocas@rosie.test").telefone("11988887777")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        Usuario outraCliente = usuarioRepository.save(Usuario.builder()
                .nome("Joana Cliente").email("cliente-trocas-outra@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        tokenAdmin = tokenService.gerar(admin);
        tokenCliente = tokenService.gerar(cliente);
        tokenOutraCliente = tokenService.gerar(outraCliente);
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Trocas " + System.nanoTime()).build());
        produto = produtoRepository.save(Produto.builder()
                .nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build());
    }

    @Test
    void defeitoCriaATrocaComFreteDaRosie() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.ENTREGUE, prazoTroca.hoje().minusDays(1));

        mockMvc.perform(comCliente(post("/trocas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pedido, "DEFEITO", "\"observacao\": \"Zíper quebrado\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("SOLICITADA"))
                .andExpect(jsonPath("$.freteConta").value("ROSIE"))
                .andExpect(jsonPath("$.pedidoId").value(pedido.getId()))
                .andExpect(jsonPath("$.tamanho").value("M"))
                .andExpect(jsonPath("$.quantidade").value(1))
                .andExpect(jsonPath("$.observacao").value("Zíper quebrado"));

        mockMvc.perform(comCliente(get("/trocas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Conjunto Serpente"))
                .andExpect(jsonPath("$[0].motivo").value("DEFEITO"));
    }

    @Test
    void freteContaVemDoMotivoMesmoQueOFrontMandeOutro() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.ENTREGUE, prazoTroca.hoje());

        mockMvc.perform(comCliente(post("/trocas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pedido, "NAO_SERVIU", "\"freteConta\": \"ROSIE\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.freteConta").value("CLIENTE"));
    }

    @Test
    void pedidoNaoEntregueForaDoPrazoEDuplicadaRespondem409() throws Exception {
        Pedido enviado = salvarPedido(StatusPedido.ENVIADO, null);
        mockMvc.perform(comCliente(post("/trocas")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(enviado, "DEFEITO", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_NAO_ENTREGUE"));

        Pedido antigo = salvarPedido(StatusPedido.ENTREGUE, prazoTroca.hoje().minusDays(30));
        mockMvc.perform(comCliente(post("/trocas")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(antigo, "DEFEITO", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TROCA_FORA_DO_PRAZO"));

        Pedido entregue = salvarPedido(StatusPedido.ENTREGUE, prazoTroca.hoje());
        mockMvc.perform(comCliente(post("/trocas")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(entregue, "DEFEITO", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(comCliente(post("/trocas")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(entregue, "DEFEITO", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TROCA_JA_SOLICITADA"));
    }

    @Test
    void pedidoDeOutraClienteResponde404() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.ENTREGUE, prazoTroca.hoje());

        mockMvc.perform(post("/trocas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOutraCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pedido, "DEFEITO", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void motivoAusenteOuInvalidoResponde400() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.ENTREGUE, prazoTroca.hoje());

        mockMvc.perform(comCliente(post("/trocas")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pedidoId\": %d, \"produtoId\": %d}".formatted(pedido.getId(), produto.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe o motivo da troca"))
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        mockMvc.perform(comCliente(post("/trocas")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pedido, "ARREPENDIMENTO", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void soClienteComToken() throws Exception {
        mockMvc.perform(get("/trocas"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/trocas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());
        mockMvc.perform(comCliente(get("/admin/trocas")))
                .andExpect(status().isForbidden());
        mockMvc.perform(comCliente(post("/admin/pedidos/{id}/entregue", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void painelListaATrocaEMudaOStatus() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.ENTREGUE, prazoTroca.hoje());
        mockMvc.perform(comCliente(post("/trocas")).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(pedido, "NAO_SERVIU", null)))
                .andExpect(status().isCreated());
        Long trocaId = ((Number) com.jayway.jsonpath.JsonPath.read(mockMvc.perform(comCliente(get("/trocas")))
                .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

        mockMvc.perform(comAdmin(get("/admin/trocas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].cliente.nome".formatted(trocaId)).value(hasItem("Maria Cliente")))
                .andExpect(jsonPath("$[?(@.id == %d)].freteConta".formatted(trocaId)).value(hasItem("CLIENTE")));

        mockMvc.perform(comAdmin(put("/admin/trocas/{id}/status", trocaId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONCLUIDA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STATUS_TROCA_INVALIDO"));
        mockMvc.perform(comAdmin(put("/admin/trocas/{id}/status", trocaId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"APROVADA\", \"resposta\": \"Pode enviar pelos Correios\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.resposta").value("Pode enviar pelos Correios"));

        mockMvc.perform(comCliente(get("/trocas")))
                .andExpect(jsonPath("$[0].status").value("APROVADA"))
                .andExpect(jsonPath("$[0].resposta").value("Pode enviar pelos Correios"));
        mockMvc.perform(comAdmin(put("/admin/trocas/{id}/status", -1)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"APROVADA\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void painelMarcaOPedidoComoEntregue() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.ENVIADO, null);
        LocalDate ontem = prazoTroca.hoje().minusDays(1);

        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/entregue", pedido.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataEntrega\": \"%s\"}".formatted(ontem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGUE"))
                .andExpect(jsonPath("$.dataEntrega").value(ontem.toString()));

        mockMvc.perform(comCliente(get("/pedidos/{id}", pedido.getId())))
                .andExpect(jsonPath("$.status").value("ENTREGUE"))
                .andExpect(jsonPath("$.dataEntrega").value(ontem.toString()))
                .andExpect(jsonPath("$.prazoTroca").value(ontem.plusDays(7).toString()));
        mockMvc.perform(comCliente(get("/pedidos")))
                .andExpect(jsonPath("$[0].prazoTroca").value(ontem.plusDays(7).toString()));
    }

    @Test
    void entregueSemCorpoEhHojeEPedidoNaoEnviadoResponde409() throws Exception {
        Pedido enviado = salvarPedido(StatusPedido.ENVIADO, null);
        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/entregue", enviado.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataEntrega").value(prazoTroca.hoje().toString()));

        Pedido pago = salvarPedido(StatusPedido.PAGO, null);
        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/entregue", pago.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_NAO_ENVIADO"));
        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/entregue", -1)))
                .andExpect(status().isNotFound());
    }

    private String corpo(Pedido pedido, String motivo, String extra) {
        return "{\"pedidoId\": %d, \"produtoId\": %d, \"tamanho\": \"M\", \"quantidade\": 1, \"motivo\": \"%s\"%s}"
                .formatted(pedido.getId(), produto.getId(), motivo, extra == null ? "" : ", " + extra);
    }

    private MockHttpServletRequestBuilder comAdmin(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin);
    }

    private MockHttpServletRequestBuilder comCliente(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente);
    }

    private Pedido salvarPedido(StatusPedido status, LocalDate dataEntrega) {
        Pedido pedido = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(status).formaPagamento(FormaPagamento.CARTAO)
                .transportadora("Correios").servicoFrete("PAC").servicoFreteId(1).prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90")).valorTotal(new BigDecimal("400.90"))
                .dataEntrega(dataEntrega)
                .build();
        pedido.getItens().add(PedidoItem.builder().pedido(pedido).produto(produto).tamanho(Tamanho.M)
                .quantidade(2).precoUnitario(new BigDecimal("189.00")).build());
        return pedidoRepository.saveAndFlush(pedido);
    }
}
