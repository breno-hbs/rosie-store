package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoCriadoDTO;
import com.rosie.backend.dto.PedidoDTO;
import com.rosie.backend.dto.PedidoRequestDTO;
import com.rosie.backend.dto.PedidoResumoDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
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
import com.rosie.backend.service.GatewayPagamento.NovaPreferencia;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Checkout com o Melhor Envio e o Mercado Pago falsos; usa o MySQL local e desfaz tudo ao fim de cada teste
@SpringBootTest
@Transactional
@Import({CalculadoraFreteFalsa.Config.class, GatewayPagamentoFalso.Config.class})
class PedidoServiceTests {

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
    private Endereco endereco;
    private Produto produto;

    @BeforeEach
    void setUp() {
        calculadora.limpar();
        gateway.limpar();
        calculadora.opcoes = List.of(
                new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7),
                new OpcaoFrete(2, "Correios", "SEDEX", new BigDecimal("38.50"), 3));

        cliente = salvarCliente("cliente-pedido@rosie.test");
        endereco = salvarEndereco(cliente);

        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Pedido " + System.nanoTime()).build());
        produto = Produto.builder()
                .nome("Vestido Aurora")
                .preco(new BigDecimal("159.90"))
                .categoria(categoria)
                .build();
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.P).quantidade(3).build());
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.M).quantidade(0).build());
        produtoRepository.save(produto);
    }

    @Test
    void criaOPedidoComOsItensDoCarrinhoEOFreteCotadoDeNovo() {
        adicionarAoCarrinho(Tamanho.P, 2);

        PedidoCriadoDTO criado = pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 2, "PIX"));

        assertThat(criado.status()).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(criado.subtotal()).isEqualByComparingTo("319.80");
        assertThat(criado.valorFrete()).isEqualByComparingTo("38.50");
        assertThat(criado.valorTotal()).isEqualByComparingTo("358.30");
        assertThat(criado.urlPagamento()).isEqualTo(
                "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-" + criado.id());

        // O frete foi cotado no servidor, para o CEP do endereço escolhido
        assertThat(calculadora.chamadas).hasSize(1);
        assertThat(calculadora.chamadas.get(0).cepDestino()).isEqualTo("03925050");

        // A preferência leva as peças e o frete, com o mesmo total
        NovaPreferencia preferencia = gateway.preferencias.get(0);
        assertThat(preferencia.pedidoId()).isEqualTo(criado.id());
        assertThat(preferencia.formaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(preferencia.emailCliente()).isEqualTo("cliente-pedido@rosie.test");
        assertThat(preferencia.linhas()).hasSize(2);
        assertThat(preferencia.linhas().get(0).titulo()).isEqualTo("Vestido Aurora (tamanho P)");
        assertThat(preferencia.linhas().get(1).titulo()).isEqualTo("Frete Correios SEDEX");
        assertThat(preferencia.linhas().stream()
                .map(linha -> linha.precoUnitario().multiply(BigDecimal.valueOf(linha.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("358.30");

        entityManager.flush();
        entityManager.clear();
        Pedido pedido = pedidoRepository.findById(criado.id()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(pedido.getServicoFreteId()).isEqualTo(2);
        assertThat(pedido.getServicoFrete()).isEqualTo("SEDEX");
        assertThat(pedido.getPrazoFreteDias()).isEqualTo(3);
        assertThat(pedido.getPreferenciaId()).isEqualTo("pref-" + criado.id());
        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("159.90");
        assertThat(pedido.getItens().get(0).getTamanho()).isEqualTo(Tamanho.P);

        // Nada de estoque nem sacola mexidos antes do pagamento
        assertThat(estoque(Tamanho.P)).isEqualTo(3);
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId())).hasSize(1);
    }

    @Test
    void oPrecoDoPedidoEODoMomentoDaCompra() {
        adicionarAoCarrinho(Tamanho.P, 1);
        PedidoCriadoDTO criado = pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "CARTAO"));

        Produto atual = produtoRepository.findById(produto.getId()).orElseThrow();
        atual.setPreco(new BigDecimal("999.00"));
        entityManager.flush();
        entityManager.clear();

        PedidoDTO pedido = pedidoService.buscar(cliente.getId(), criado.id());
        assertThat(pedido.itens().get(0).precoUnitario()).isEqualByComparingTo("159.90");
        assertThat(pedido.subtotal()).isEqualByComparingTo("159.90");
        assertThat(pedido.valorTotal()).isEqualByComparingTo("182.80");
        assertThat(pedido.formaPagamento()).isEqualTo("CARTAO");
        assertThat(pedido.endereco().cep()).isEqualTo("03925050");
        assertThat(pedido.urlPagamento()).isNotBlank();
    }

    @Test
    void estoqueInsuficienteNaHoraDoPedidoDa409SemCriarNada() {
        adicionarAoCarrinho(Tamanho.P, 2);
        // Outra cliente comprou enquanto a peça estava na sacola
        produtoRepository.findById(produto.getId()).orElseThrow().getEstoque().stream()
                .filter(e -> e.getTamanho() == Tamanho.P).findFirst().orElseThrow().setQuantidade(1);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "PIX")))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Só temos 1 de Vestido Aurora (P). Ajuste a quantidade na sacola.")
                .extracting("codigo").isEqualTo("ESTOQUE_INSUFICIENTE");
        assertThat(gateway.preferencias).isEmpty();
        assertThat(pedidoRepository.findByUsuarioId(cliente.getId())).isEmpty();
    }

    @Test
    void tamanhoEsgotadoEProdutoDesativadoTambemTravam() {
        adicionarAoCarrinho(Tamanho.M, 1);
        assertThatThrownBy(() -> pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "PIX")))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("ESTOQUE_INSUFICIENTE");

        carrinhoItemRepository.deleteAll(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId()));
        adicionarAoCarrinho(Tamanho.P, 1);
        Produto atual = produtoRepository.findById(produto.getId()).orElseThrow();
        atual.setAtivo(false);
        entityManager.flush();
        entityManager.clear();
        assertThatThrownBy(() -> pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "PIX")))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("PRODUTO_INDISPONIVEL");
    }

    @Test
    void opcaoDeFreteQueNaoVoltouNaCotacaoDa409() {
        adicionarAoCarrinho(Tamanho.P, 1);
        // O front mandou o id de um serviço que não atende mais o CEP
        assertThatThrownBy(() -> pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 17, "PIX")))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("FRETE_ALTERADO");
        assertThat(gateway.preferencias).isEmpty();
    }

    @Test
    void carrinhoVazioEEnderecoDeOutraClienteSaoRecusados() {
        assertThatThrownBy(() -> pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "PIX")))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("CARRINHO_VAZIO");

        adicionarAoCarrinho(Tamanho.P, 1);
        Endereco alheio = salvarEndereco(salvarCliente("outra-pedido@rosie.test"));
        assertThatThrownBy(() -> pedidoService.criar(cliente.getId(), new PedidoRequestDTO(alheio.getId(), 1, "PIX")))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Endereço de entrega não encontrado");
    }

    @Test
    void pedidoDeOutraClienteNaoAparece() {
        adicionarAoCarrinho(Tamanho.P, 1);
        PedidoCriadoDTO criado = pedidoService.criar(cliente.getId(), new PedidoRequestDTO(endereco.getId(), 1, "PIX"));
        Usuario outra = salvarCliente("outra-busca@rosie.test");

        assertThatThrownBy(() -> pedidoService.buscar(outra.getId(), criado.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listaSoOsPedidosDaClienteDoMaisRecenteParaOMaisAntigo() {
        Pedido entregue = salvarPedido(cliente, endereco, StatusPedido.ENTREGUE, "211.90");
        Pedido cancelado = salvarPedido(cliente, endereco, StatusPedido.CANCELADO, "109.00");
        Pedido aguardando = salvarPedido(cliente, endereco, StatusPedido.AGUARDANDO_PAGAMENTO, "189.00");
        Usuario outra = salvarCliente("outra-lista@rosie.test");
        salvarPedido(outra, salvarEndereco(outra), StatusPedido.PAGO, "50.00");
        // A ordem é pela data, não pelo id: o cancelado foi criado depois, mas com data mais antiga
        mudarData(entregue, "2026-08-20 14:30:00");
        mudarData(cancelado, "2026-08-02 09:00:00");
        mudarData(aguardando, "2026-10-18 10:00:00");

        List<PedidoResumoDTO> pedidos = pedidoService.listar(cliente.getId());

        assertThat(pedidos).extracting(PedidoResumoDTO::id)
                .containsExactly(aguardando.getId(), entregue.getId(), cancelado.getId());
        PedidoResumoDTO primeiro = pedidos.get(0);
        assertThat(primeiro.status()).isEqualTo("AGUARDANDO_PAGAMENTO");
        assertThat(primeiro.valorTotal()).isEqualByComparingTo("189.00");
        assertThat(primeiro.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 18, 10, 0));

        assertThat(pedidoService.listar(salvarCliente("sem-pedidos@rosie.test").getId())).isEmpty();
    }

    @Test
    void oDetalheTrazORastreioQuandoExiste() {
        Pedido pedido = salvarPedido(cliente, endereco, StatusPedido.ENVIADO, "211.90");
        pedido.setCodigoRastreio("AA123456789BR");
        entityManager.flush();
        entityManager.clear();

        PedidoDTO detalhe = pedidoService.buscar(cliente.getId(), pedido.getId());

        assertThat(detalhe.status()).isEqualTo("ENVIADO");
        assertThat(detalhe.codigoRastreio()).isEqualTo("AA123456789BR");
        assertThat(detalhe.transportadora()).isEqualTo("Correios");
        assertThat(detalhe.valorFrete()).isEqualByComparingTo("22.90");
        assertThat(detalhe.subtotal()).isEqualByComparingTo("189.00");
        assertThat(detalhe.endereco().logradouro()).isEqualTo("Rua das Flores");
        // Pedido pago não oferece mais o link de pagamento
        assertThat(detalhe.urlPagamento()).isNull();
    }

    private Pedido salvarPedido(Usuario dona, Endereco destino, StatusPedido status, String total) {
        return pedidoRepository.saveAndFlush(Pedido.builder()
                .usuario(dona)
                .endereco(destino)
                .status(status)
                .formaPagamento(FormaPagamento.PIX)
                .transportadora("Correios")
                .servicoFrete("PAC")
                .prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90"))
                .valorTotal(new BigDecimal(total))
                .urlPagamento("https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=teste")
                .build());
    }

    // created_at é preenchido pelo Hibernate no insert; aqui volta no tempo direto no banco
    private void mudarData(Pedido pedido, String data) {
        entityManager.createNativeQuery("update pedido set created_at = ?1 where id = ?2")
                .setParameter(1, data)
                .setParameter(2, pedido.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private void adicionarAoCarrinho(Tamanho tamanho, int quantidade) {
        carrinhoItemRepository.save(CarrinhoItem.builder()
                .usuario(cliente)
                .produto(produto)
                .tamanho(tamanho)
                .quantidade(quantidade)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    private int estoque(Tamanho tamanho) {
        entityManager.flush();
        entityManager.clear();
        return produtoRepository.findById(produto.getId()).orElseThrow().getEstoque().stream()
                .filter(e -> e.getTamanho() == tamanho).findFirst().orElseThrow().getQuantidade();
    }

    private Usuario salvarCliente(String email) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Cliente Pedido")
                .email(email)
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());
    }

    private Endereco salvarEndereco(Usuario dona) {
        return enderecoRepository.save(Endereco.builder()
                .usuario(dona)
                .cep("03925050")
                .logradouro("Rua das Flores")
                .numero("120")
                .bairro("Jardim Iva")
                .cidade("São Paulo")
                .estado("SP")
                .build());
    }
}
