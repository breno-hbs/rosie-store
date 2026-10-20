package com.rosie.backend.controller;

import com.rosie.backend.exception.EtiquetaFalhouException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.model.MotivoTroca;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Troca;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.TrocaRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /admin/trocas/{id}/etiqueta-volta e o que a cliente vê em GET /trocas (logística reversa, #63), com o
// Melhor Envio falso e o endereço da loja configurado; MySQL local, desfeito ao fim
@SpringBootTest(properties = {
        "rosie.etiqueta.remetente.nome=Rosie Store",
        "rosie.etiqueta.remetente.logradouro=Avenida Paulista",
        "rosie.etiqueta.remetente.numero=1000",
        "rosie.etiqueta.remetente.bairro=Bela Vista",
        "rosie.etiqueta.remetente.cidade=São Paulo",
        "rosie.etiqueta.remetente.uf=SP",
        "rosie.frete.cep-origem=01310100"
})
@AutoConfigureMockMvc
@Transactional
@Import(EmissorEtiquetaFalso.Config.class)
class TrocaEtiquetaVoltaControllerTest {

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
    private TrocaRepository trocaRepository;

    @Autowired
    private TokenService tokenService;

    private String tokenAdmin;
    private String tokenCliente;
    private Pedido pedido;
    private Produto produto;

    @BeforeEach
    void setUp() {
        emissor.limpar();
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Roseane Teste").email("admin-volta@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.ADMIN).build());
        Usuario cliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Cliente").email("cliente-volta-api@rosie.test").telefone("11988887777")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        tokenAdmin = tokenService.gerar(admin);
        tokenCliente = tokenService.gerar(cliente);
        Endereco endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Volta API " + System.nanoTime()).build());
        produto = produtoRepository.save(Produto.builder()
                .nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build());
        Pedido novo = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(StatusPedido.ENTREGUE).formaPagamento(FormaPagamento.CARTAO)
                .transportadora("Correios").servicoFrete("PAC").servicoFreteId(1).prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90")).valorTotal(new BigDecimal("211.90"))
                .build();
        novo.getItens().add(PedidoItem.builder().pedido(novo).produto(produto).tamanho(Tamanho.M)
                .quantidade(1).precoUnitario(new BigDecimal("189.00")).build());
        pedido = pedidoRepository.saveAndFlush(novo);
    }

    @Test
    void painelGeraAEtiquetaDeVoltaEAClienteVeNaMinhaConta() throws Exception {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);

        mockMvc.perform(comAdmin(get("/admin/trocas")))
                .andExpect(jsonPath("$[?(@.id == %d)].etiquetaVolta.situacao".formatted(troca.getId()))
                        .value(hasItem("SEM_ETIQUETA")))
                .andExpect(jsonPath("$[?(@.id == %d)].etiquetaVolta.valorEstimado".formatted(troca.getId()))
                        .value(hasItem(22.9)));

        mockMvc.perform(comAdmin(post("/admin/trocas/{id}/etiqueta-volta", troca.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(troca.getId()))
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.etiquetaVolta.situacao").value("GERADA"))
                .andExpect(jsonPath("$.etiquetaVolta.envioId").value("envio-1"))
                .andExpect(jsonPath("$.etiquetaVolta.urlEtiqueta").value(EmissorEtiquetaFalso.LINK_PUBLICO + "envio-1"))
                .andExpect(jsonPath("$.etiquetaVolta.codigoRastreio").value("ME123456789BR"))
                .andExpect(jsonPath("$.etiquetaVolta.erro").isEmpty());
        // Repetir devolve a mesma etiqueta
        mockMvc.perform(comAdmin(post("/admin/trocas/{id}/etiqueta-volta", troca.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etiquetaVolta.envioId").value("envio-1"));

        mockMvc.perform(comCliente(get("/trocas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiquetaVolta.situacao").value("GERADA"))
                .andExpect(jsonPath("$[0].etiquetaVolta.urlEtiqueta").value(EmissorEtiquetaFalso.LINK_PUBLICO + "envio-1"))
                .andExpect(jsonPath("$[0].etiquetaVolta.codigoRastreio").value("ME123456789BR"))
                // O id do envio e o erro são só do painel
                .andExpect(jsonPath("$[0].etiquetaVolta.envioId").doesNotExist())
                .andExpect(jsonPath("$[0].etiquetaVolta.erro").doesNotExist())
                .andExpect(jsonPath("$[0].enderecoDevolucao").isEmpty());
    }

    @Test
    void trocaNaoAprovadaOuComFreteDaClienteResponde409() throws Exception {
        Troca solicitada = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.SOLICITADA);
        mockMvc.perform(comAdmin(post("/admin/trocas/{id}/etiqueta-volta", solicitada.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TROCA_NAO_APROVADA"))
                .andExpect(jsonPath("$.erro").value("Só troca aprovada tem etiqueta de volta: esta está SOLICITADA."));

        Troca naoServiu = salvarTroca(MotivoTroca.NAO_SERVIU, StatusTroca.APROVADA);
        mockMvc.perform(comAdmin(post("/admin/trocas/{id}/etiqueta-volta", naoServiu.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TROCA_FRETE_DA_CLIENTE"));

        mockMvc.perform(comAdmin(post("/admin/trocas/{id}/etiqueta-volta", -1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void naoServiuAprovadaMostraAClienteOEnderecoDaLoja() throws Exception {
        salvarTroca(MotivoTroca.NAO_SERVIU, StatusTroca.APROVADA);

        mockMvc.perform(comCliente(get("/trocas")))
                .andExpect(jsonPath("$[0].etiquetaVolta.situacao").value("POR_CONTA_DA_CLIENTE"))
                .andExpect(jsonPath("$[0].enderecoDevolucao.nome").value("Rosie Store"))
                .andExpect(jsonPath("$[0].enderecoDevolucao.logradouro").value("Avenida Paulista"))
                .andExpect(jsonPath("$[0].enderecoDevolucao.complemento").isEmpty())
                .andExpect(jsonPath("$[0].enderecoDevolucao.cep").value("01310-100"));
    }

    @Test
    void falhaDoMelhorEnvioResponde502EOPainelMostraAFalha() throws Exception {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);
        emissor.falhas.put("comprar", new EtiquetaFalhouException("O Melhor Envio recusou a etiqueta: Saldo insuficiente"));

        mockMvc.perform(comAdmin(post("/admin/trocas/{id}/etiqueta-volta", troca.getId())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("ETIQUETA_FALHOU"))
                .andExpect(jsonPath("$.erro").value("O Melhor Envio recusou a etiqueta: Saldo insuficiente"));

        mockMvc.perform(comAdmin(get("/admin/trocas")))
                .andExpect(jsonPath("$[?(@.id == %d)].status".formatted(troca.getId())).value(hasItem("APROVADA")))
                .andExpect(jsonPath("$[?(@.id == %d)].etiquetaVolta.situacao".formatted(troca.getId()))
                        .value(hasItem("FALHOU")))
                .andExpect(jsonPath("$[?(@.id == %d)].etiquetaVolta.erro".formatted(troca.getId()))
                        .value(hasItem("O Melhor Envio recusou a etiqueta: Saldo insuficiente")));
    }

    @Test
    void soOPainelGeraAEtiquetaDeVolta() throws Exception {
        Troca troca = salvarTroca(MotivoTroca.DEFEITO, StatusTroca.APROVADA);

        mockMvc.perform(post("/admin/trocas/{id}/etiqueta-volta", troca.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(comCliente(post("/admin/trocas/{id}/etiqueta-volta", troca.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        assertThat(emissor.enviosNoCarrinho).isEmpty();
    }

    private Troca salvarTroca(MotivoTroca motivo, StatusTroca status) {
        return trocaRepository.saveAndFlush(Troca.builder()
                .pedido(pedido).produto(produto).tamanho(Tamanho.M).quantidade(1)
                .motivo(motivo).status(status).build());
    }

    private MockHttpServletRequestBuilder comAdmin(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin);
    }

    private MockHttpServletRequestBuilder comCliente(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenCliente);
    }
}
