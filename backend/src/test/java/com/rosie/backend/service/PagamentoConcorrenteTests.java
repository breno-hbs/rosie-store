package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoCriadoDTO;
import com.rosie.backend.dto.PedidoRequestDTO;
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
import com.rosie.backend.service.GatewayPagamento.StatusPagamento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Dois avisos do mesmo pagamento aprovado chegando ao mesmo tempo, cada um na sua transação (sem a
// transação do teste, que esconderia a disputa). Grava de verdade no MySQL e apaga tudo no fim.
@SpringBootTest
@Import({CalculadoraFreteFalsa.Config.class, GatewayPagamentoFalso.Config.class})
class PagamentoConcorrenteTests {

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
    private TransactionTemplate transacao;

    private Usuario cliente;
    private Endereco endereco;
    private Categoria categoria;
    private Produto produto;
    private PedidoCriadoDTO pedido;

    @BeforeEach
    void setUp() {
        calculadora.limpar();
        gateway.limpar();
        calculadora.opcoes = List.of(new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7));

        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Concorrente").email("cliente-concorrente-" + System.nanoTime() + "@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        categoria = categoriaRepository.save(Categoria.builder().nome("Concorrente " + System.nanoTime()).build());
        Produto novo = Produto.builder().nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build();
        novo.getEstoque().add(ProdutoEstoque.builder().produto(novo).tamanho(Tamanho.M).quantidade(5).build());
        produto = produtoRepository.save(novo);
        carrinhoItemRepository.save(CarrinhoItem.builder()
                .usuario(cliente).produto(produto).tamanho(Tamanho.M).quantidade(2).build());

        pedido = pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "PIX"));
    }

    @AfterEach
    void limparBanco() {
        transacao.executeWithoutResult(status -> {
            pedidoRepository.deleteById(pedido.id());
            carrinhoItemRepository.deleteAll(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId()));
            produtoRepository.deleteById(produto.getId());
            categoriaRepository.deleteById(categoria.getId());
            enderecoRepository.deleteById(endereco.getId());
            usuarioRepository.deleteById(cliente.getId());
        });
    }

    @Test
    void doisAvisosSimultaneosBaixamOEstoqueUmaVezSo() throws Exception {
        // 2 x 189,00 + 22,90
        gateway.programar("pg-1", StatusPagamento.APROVADO, pedido.id(), new BigDecimal("400.90"));

        CountDownLatch largada = new CountDownLatch(1);
        Callable<Void> aviso = () -> {
            largada.await();
            pagamentoService.processar("pg-1");
            return null;
        };
        ExecutorService threads = Executors.newFixedThreadPool(2);
        List<Future<Void>> resultados = new ArrayList<>();
        try {
            resultados.add(threads.submit(aviso));
            resultados.add(threads.submit(aviso));
            largada.countDown();
            for (Future<Void> resultado : resultados) {
                resultado.get(30, TimeUnit.SECONDS);
            }
        } finally {
            threads.shutdownNow();
        }

        transacao.executeWithoutResult(status -> {
            assertThat(pedidoRepository.findById(pedido.id()).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGO);
            assertThat(produtoRepository.findById(produto.getId()).orElseThrow().getEstoque().get(0).getQuantidade())
                    .isEqualTo(3);
        });
    }
}
