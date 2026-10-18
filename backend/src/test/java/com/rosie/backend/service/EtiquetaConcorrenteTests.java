package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoAdminDTO;
import com.rosie.backend.exception.EtiquetaFalhouException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sem a transação do teste (que esconderia o que é confirmado no banco): cada passo da etiqueta precisa ficar
// gravado mesmo se um passo seguinte falhar, e dois cliques juntos compram uma etiqueta só. Grava de verdade
// no MySQL e apaga tudo no fim.
@SpringBootTest
@Import(EmissorEtiquetaFalso.Config.class)
class EtiquetaConcorrenteTests {

    @Autowired
    private EtiquetaService etiquetaService;

    @Autowired
    private EmissorEtiquetaFalso emissor;

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
    private TransactionTemplate transacao;

    private Usuario cliente;
    private Endereco endereco;
    private Categoria categoria;
    private Produto produto;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        emissor.limpar();
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Etiqueta Concorrente").email("cliente-etiqueta-" + System.nanoTime() + "@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        categoria = categoriaRepository.save(Categoria.builder().nome("Etiqueta concorrente " + System.nanoTime()).build());
        produto = produtoRepository.save(Produto.builder()
                .nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build());

        Pedido novo = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(StatusPedido.PAGO).formaPagamento(FormaPagamento.PIX)
                .transportadora("Correios").servicoFrete("PAC").servicoFreteId(1).prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90")).valorTotal(new BigDecimal("211.90"))
                .build();
        novo.getItens().add(PedidoItem.builder().pedido(novo).produto(produto).tamanho(Tamanho.M)
                .quantidade(1).precoUnitario(new BigDecimal("189.00")).build());
        pedido = pedidoRepository.save(novo);
    }

    @AfterEach
    void limparBanco() {
        transacao.executeWithoutResult(status -> {
            pedidoRepository.deleteById(pedido.getId());
            produtoRepository.deleteById(produto.getId());
            categoriaRepository.deleteById(categoria.getId());
            enderecoRepository.deleteById(endereco.getId());
            usuarioRepository.deleteById(cliente.getId());
        });
    }

    @Test
    void envioNoCarrinhoFicaGravadoMesmoComFalhaNaCompra() {
        emissor.falhas.put("comprar", new EtiquetaFalhouException("Saldo insuficiente"));

        assertThatThrownBy(() -> etiquetaService.gerar(pedido.getId())).isInstanceOf(EtiquetaFalhouException.class);

        Pedido salvo = pedidoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(salvo.getEnvioId()).isEqualTo("envio-1");
        assertThat(salvo.getStatus()).isEqualTo(StatusPedido.PAGO);

        emissor.falhas.clear();
        etiquetaService.gerar(pedido.getId());
        assertThat(emissor.enviosNoCarrinho).hasSize(1);
        assertThat(emissor.compras).containsExactly("envio-1");
    }

    @Test
    void doisCliquesSimultaneosCompramUmaEtiquetaSo() throws Exception {
        emissor.demoraNoCarrinhoMs = 300;

        CountDownLatch largada = new CountDownLatch(1);
        Callable<PedidoAdminDTO> clique = () -> {
            largada.await();
            return etiquetaService.gerar(pedido.getId());
        };
        ExecutorService threads = Executors.newFixedThreadPool(2);
        List<Future<PedidoAdminDTO>> resultados = new ArrayList<>();
        try {
            resultados.add(threads.submit(clique));
            resultados.add(threads.submit(clique));
            largada.countDown();
            for (Future<PedidoAdminDTO> resultado : resultados) {
                assertThat(resultado.get(30, TimeUnit.SECONDS).codigoRastreio()).isEqualTo("ME123456789BR");
            }
        } finally {
            threads.shutdownNow();
        }

        assertThat(emissor.enviosNoCarrinho).hasSize(1);
        assertThat(emissor.compras).hasSize(1);
        assertThat(pedidoRepository.findById(pedido.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.ENVIADO);
    }
}
