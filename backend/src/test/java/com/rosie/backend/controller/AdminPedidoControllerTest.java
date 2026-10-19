package com.rosie.backend.controller;

import com.rosie.backend.exception.EtiquetaFalhouException;
import com.rosie.backend.exception.EtiquetaIndisponivelException;
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
import com.rosie.backend.service.EmissorEtiquetaFalso;
import com.rosie.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /admin/pedidos, GET /admin/pedidos/{id} e POST /admin/pedidos/{id}/etiqueta contra o MySQL, com o
// Melhor Envio falso
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(EmissorEtiquetaFalso.Config.class)
class AdminPedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmissorEtiquetaFalso emissor;

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

    private String tokenAdmin;
    private String tokenCliente;
    private Usuario cliente;
    private Endereco endereco;
    private Produto produto;

    @BeforeEach
    void setUp() {
        emissor.limpar();
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste").email("admin-pedidos@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.ADMIN).build());
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Cliente").email("cliente-admin-pedidos@rosie.test").telefone("11988887777")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        tokenAdmin = tokenService.gerar(admin);
        tokenCliente = tokenService.gerar(cliente);
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Admin pedidos " + System.nanoTime()).build());
        produto = produtoRepository.save(Produto.builder()
                .nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build());
    }

    @Test
    void listaOsPedidosComClienteStatusETotal() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.PAGO, 2);

        mockMvc.perform(comAdmin(get("/admin/pedidos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].status".formatted(pedido.getId())).value(hasItem("PAGO")))
                .andExpect(jsonPath("$[?(@.id == %d)].cliente.nome".formatted(pedido.getId()))
                        .value(hasItem("Maria Cliente")))
                .andExpect(jsonPath("$[?(@.id == %d)].quantidadePecas".formatted(pedido.getId())).value(hasItem(2)))
                .andExpect(jsonPath("$[?(@.id == %d)].valorTotal".formatted(pedido.getId())).value(hasItem(400.90)));
    }

    @Test
    void detalheTrazItensEnderecoFreteEClienteDeQualquerCliente() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.PAGO, 2);

        mockMvc.perform(comAdmin(get("/admin/pedidos/{id}", pedido.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.cliente.email").value("cliente-admin-pedidos@rosie.test"))
                .andExpect(jsonPath("$.cliente.telefone").value("11988887777"))
                .andExpect(jsonPath("$.subtotal").value(378.00))
                .andExpect(jsonPath("$.valorFrete").value(22.90))
                .andExpect(jsonPath("$.transportadora").value("Correios"))
                .andExpect(jsonPath("$.servicoFrete").value("PAC"))
                .andExpect(jsonPath("$.endereco.cep").value("03925050"))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].tamanho").value("M"))
                .andExpect(jsonPath("$.urlEtiqueta").value(nullValue()))
                .andExpect(jsonPath("$.codigoRastreio").value(nullValue()));
    }

    @Test
    void geraAEtiquetaEOClienteVeORastreio() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.PAGO, 1);

        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/etiqueta", pedido.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENVIADO"))
                .andExpect(jsonPath("$.codigoRastreio").value("ME123456789BR"))
                .andExpect(jsonPath("$.urlEtiqueta").value(EmissorEtiquetaFalso.LINK + "envio-1"));

        // De novo: devolve a mesma etiqueta, sem comprar outra
        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/etiqueta", pedido.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envioId").value("envio-1"));
        assertThat(emissor.compras).hasSize(1);

        mockMvc.perform(get("/pedidos/{id}", pedido.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENVIADO"))
                .andExpect(jsonPath("$.codigoRastreio").value("ME123456789BR"))
                // O link da etiqueta é só do painel
                .andExpect(jsonPath("$.urlEtiqueta").doesNotExist());
    }

    @Test
    void pedidoNaoPagoResponde409() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.AGUARDANDO_PAGAMENTO, 1);

        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/etiqueta", pedido.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_NAO_PAGO"));
    }

    @Test
    void falhasDoMelhorEnvioViram502E503() throws Exception {
        Pedido pedido = salvarPedido(StatusPedido.PAGO, 1);

        emissor.falhas.put("carrinho", new EtiquetaFalhouException("O Melhor Envio recusou a etiqueta: Saldo insuficiente"));
        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/etiqueta", pedido.getId())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("ETIQUETA_FALHOU"))
                .andExpect(jsonPath("$.erro").value("O Melhor Envio recusou a etiqueta: Saldo insuficiente"));

        emissor.falhas.put("carrinho", new EtiquetaIndisponivelException("A etiqueta de envio não está configurada no servidor"));
        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/etiqueta", pedido.getId())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("ETIQUETA_INDISPONIVEL"));
    }

    @Test
    void pedidoInexistenteResponde404() throws Exception {
        mockMvc.perform(comAdmin(get("/admin/pedidos/{id}", -1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mockMvc.perform(comAdmin(post("/admin/pedidos/{id}/etiqueta", -1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void soAdmin() throws Exception {
        mockMvc.perform(get("/admin/pedidos"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/pedidos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/pedidos/{id}/etiqueta", 1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder comAdmin(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin);
    }

    private Pedido salvarPedido(StatusPedido status, int quantidade) {
        BigDecimal subtotal = new BigDecimal("189.00").multiply(BigDecimal.valueOf(quantidade));
        Pedido pedido = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(status).formaPagamento(FormaPagamento.CARTAO)
                .transportadora("Correios").servicoFrete("PAC").servicoFreteId(1).prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90")).valorTotal(subtotal.add(new BigDecimal("22.90")))
                .build();
        pedido.getItens().add(PedidoItem.builder().pedido(pedido).produto(produto).tamanho(Tamanho.M)
                .quantidade(quantidade).precoUnitario(new BigDecimal("189.00")).build());
        return pedidoRepository.saveAndFlush(pedido);
    }
}
