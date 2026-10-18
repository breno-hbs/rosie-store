package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoCriadoDTO;
import com.rosie.backend.dto.PedidoRequestDTO;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
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
import com.rosie.backend.service.GatewayPagamento.StatusPagamento;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Pagamento aprovado, recusado e cancelado vindo do Mercado Pago (falso); MySQL local, desfeito ao fim
@SpringBootTest
@Transactional
@Import({CalculadoraFreteFalsa.Config.class, GatewayPagamentoFalso.Config.class})
class PagamentoServiceTests {

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private CalculadoraFreteFalsa calculadora;

    @Autowired
    private GatewayPagamentoFalso gateway;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private CarrinhoItemRepository carrinhoItemRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EntityManager entityManager;

    private Usuario cliente;
    private Produto produto;
    private Produto outroProduto;
    private PedidoCriadoDTO pedido;

    @BeforeEach
    void setUp() {
        calculadora.limpar();
        gateway.limpar();
        calculadora.opcoes = List.of(new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7));

        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Pagamento")
                .email("cliente-pagamento@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());
        Endereco endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());

        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Pagamento " + System.nanoTime()).build());
        produto = salvarProduto(categoria, "Vestido Aurora", "159.90", 3);
        outroProduto = salvarProduto(categoria, "Calça Linho", "120.00", 5);

        adicionarAoCarrinho(produto, 2);
        adicionarAoCarrinho(outroProduto, 1);
        // Total: 2 x 159,90 + 120,00 + 22,90 de frete = 462,70
        pedido = pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "CARTAO"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void aprovadoBaixaOEstoqueEsvaziaASacolaUmaVezSo() {
        gateway.programar("pg-1", StatusPagamento.APROVADO, pedido.id(), new BigDecimal("462.70"));

        pagamentoService.processar("pg-1");
        // O Mercado Pago repete o aviso
        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();

        Pedido pago = pedidoRepository.findById(pedido.id()).orElseThrow();
        assertThat(pago.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(pago.getPagamentoId()).isEqualTo("pg-1");
        assertThat(pago.getPagoEm()).isNotNull();
        assertThat(estoque(produto)).isEqualTo(1);
        assertThat(estoque(outroProduto)).isEqualTo(4);
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId())).isEmpty();
        assertThat(gateway.consultas).containsExactly("pg-1", "pg-1");
    }

    @Test
    void sacolaGuardaOQueEntrouDepoisDoCheckout() {
        // Depois de criar o pedido a cliente pôs mais uma peça igual na sacola
        CarrinhoItem item = carrinhoItemRepository
                .findByUsuarioIdAndProdutoIdAndTamanho(cliente.getId(), produto.getId(), Tamanho.P).orElseThrow();
        item.setQuantidade(3);
        entityManager.flush();
        gateway.programar("pg-1", StatusPagamento.APROVADO, pedido.id(), new BigDecimal("462.70"));

        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();

        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId()))
                .singleElement()
                .satisfies(restante -> {
                    assertThat(restante.getProduto().getId()).isEqualTo(produto.getId());
                    assertThat(restante.getQuantidade()).isEqualTo(1);
                });
    }

    @Test
    void aprovadoSemEstoqueFicaComPendenciaSemEstoqueNegativo() {
        // Outra cliente levou uma das peças entre o checkout e a aprovação
        definirEstoque(produto, 1);
        gateway.programar("pg-1", StatusPagamento.APROVADO, pedido.id(), new BigDecimal("462.70"));

        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();

        Pedido pago = pedidoRepository.findById(pedido.id()).orElseThrow();
        assertThat(pago.getStatus()).isEqualTo(StatusPedido.PAGO_COM_PENDENCIA);
        assertThat(pago.getPendencia()).contains("Vestido Aurora P (pedido 2, em estoque 1)");
        assertThat(pago.getPagamentoId()).isEqualTo("pg-1");
        // Nada baixou, nem a peça que tinha estoque
        assertThat(estoque(produto)).isEqualTo(1);
        assertThat(estoque(outroProduto)).isEqualTo(5);
        // A compra foi paga: as peças saem da sacola
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId())).isEmpty();

        // Repetir o aviso não muda nada
        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();
        assertThat(estoque(produto)).isEqualTo(1);
    }

    @Test
    void valorPagoDiferenteDoTotalFicaComPendencia() {
        gateway.programar("pg-1", StatusPagamento.APROVADO, pedido.id(), new BigDecimal("1.00"));

        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();

        Pedido pago = pedidoRepository.findById(pedido.id()).orElseThrow();
        assertThat(pago.getStatus()).isEqualTo(StatusPedido.PAGO_COM_PENDENCIA);
        assertThat(pago.getPendencia()).startsWith("Valor pago (1.00) diferente do total do pedido (462.70)");
        assertThat(estoque(produto)).isEqualTo(3);
    }

    @Test
    void recusadoNaoMexeNoEstoqueEOutraTentativaAprovadaFecha() {
        gateway.programar("pg-1", StatusPagamento.RECUSADO, pedido.id(), new BigDecimal("462.70"));
        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();

        assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.PAGAMENTO_RECUSADO);
        assertThat(estoque(produto)).isEqualTo(3);
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId())).hasSize(2);

        // A cliente tenta outro cartão pelo mesmo link
        gateway.programar("pg-2", StatusPagamento.APROVADO, pedido.id(), new BigDecimal("462.70"));
        pagamentoService.processar("pg-2");
        // Um aviso atrasado da tentativa recusada não desfaz o pagamento
        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();

        assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(estoque(produto)).isEqualTo(1);
    }

    @Test
    void canceladoEPendenteNaoMexemNoEstoque() {
        gateway.programar("pg-1", StatusPagamento.PENDENTE, pedido.id(), new BigDecimal("462.70"));
        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();
        assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);

        gateway.programar("pg-1", StatusPagamento.CANCELADO, pedido.id(), new BigDecimal("462.70"));
        pagamentoService.processar("pg-1");
        entityManager.flush();
        entityManager.clear();
        assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(estoque(produto)).isEqualTo(3);
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId())).hasSize(2);
    }

    @Test
    void pagamentoSemPedidoConhecidoEIgnorado() {
        gateway.pagamentos.put("pg-x", new GatewayPagamento.Pagamento("pg-x", StatusPagamento.APROVADO, null,
                new BigDecimal("10.00")));
        gateway.programar("pg-y", StatusPagamento.APROVADO, 999_999_999L, new BigDecimal("10.00"));

        pagamentoService.processar("pg-x");
        pagamentoService.processar("pg-y");
        entityManager.flush();
        entityManager.clear();

        assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(estoque(produto)).isEqualTo(3);
    }

    @Test
    void simulacaoNaoExisteForaDoModoSimulado() {
        assertThatThrownBy(() -> pagamentoService.simular(cliente.getId(), pedido.id(), StatusPagamento.APROVADO))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(estoque(produto)).isEqualTo(3);
    }

    private Produto salvarProduto(Categoria categoria, String nome, String preco, int estoqueP) {
        Produto novo = Produto.builder().nome(nome).preco(new BigDecimal(preco)).categoria(categoria).build();
        novo.getEstoque().add(ProdutoEstoque.builder().produto(novo).tamanho(Tamanho.P).quantidade(estoqueP).build());
        return produtoRepository.save(novo);
    }

    private void adicionarAoCarrinho(Produto peca, int quantidade) {
        carrinhoItemRepository.save(CarrinhoItem.builder()
                .usuario(cliente).produto(peca).tamanho(Tamanho.P).quantidade(quantidade).build());
        entityManager.flush();
        entityManager.clear();
    }

    private void definirEstoque(Produto peca, int quantidade) {
        produtoRepository.findById(peca.getId()).orElseThrow().getEstoque().get(0).setQuantidade(quantidade);
        entityManager.flush();
        entityManager.clear();
    }

    private int estoque(Produto peca) {
        return produtoRepository.findById(peca.getId()).orElseThrow().getEstoque().get(0).getQuantidade();
    }
}
