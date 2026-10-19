package com.rosie.backend.service;

import com.rosie.backend.dto.PedidoAdminDTO;
import com.rosie.backend.dto.PedidoResumoDTO;
import com.rosie.backend.dto.TrocaAdminDTO;
import com.rosie.backend.dto.TrocaDTO;
import com.rosie.backend.dto.TrocaRequestDTO;
import com.rosie.backend.dto.TrocaStatusRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
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
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Troca e devolução (#27) e pedido entregue no painel contra o MySQL local; cada teste é desfeito no fim.
// O prazo é o padrão de 7 dias (rosie.troca.prazo-dias).
@SpringBootTest
@Transactional
class TrocaServiceTests {

    @Autowired
    private TrocaService trocaService;

    @Autowired
    private PedidoAdminService pedidoAdminService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PrazoTroca prazoTroca;

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

    private Usuario cliente;
    private Usuario outraCliente;
    private Endereco endereco;
    private Produto produto;
    private Produto outroProduto;

    @BeforeEach
    void setUp() {
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Maria Cliente").email("troca-servico@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        outraCliente = usuarioRepository.save(Usuario.builder()
                .nome("Joana Cliente").email("troca-servico-outra@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
        endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Troca " + System.nanoTime()).build());
        produto = produtoRepository.save(Produto.builder()
                .nome("Conjunto Serpente").preco(new BigDecimal("189.00")).categoria(categoria).build());
        outroProduto = produtoRepository.save(Produto.builder()
                .nome("Vestido Aurora").preco(new BigDecimal("229.00")).categoria(categoria).build());
    }

    // --- Regra de frete (RNF08) ---

    @Test
    void defeitoFicaComOFreteDaRosie() {
        Pedido pedido = entregue(hoje().minusDays(2));

        TrocaDTO troca = trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.DEFEITO));

        assertThat(troca.freteConta()).isEqualTo("ROSIE");
        assertThat(troca.status()).isEqualTo("SOLICITADA");
        assertThat(troca.motivo()).isEqualTo("DEFEITO");
        assertThat(troca.pedidoId()).isEqualTo(pedido.getId());
        assertThat(troca.nome()).isEqualTo("Conjunto Serpente");
        assertThat(troca.tamanho()).isEqualTo("M");
        assertThat(troca.quantidade()).isEqualTo(2);
    }

    @Test
    void naoServiuFicaComOFreteDaCliente() {
        Pedido pedido = entregue(hoje().minusDays(2));

        TrocaDTO troca = trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.NAO_SERVIU));

        assertThat(troca.freteConta()).isEqualTo("CLIENTE");
    }

    // --- Quem pode pedir ---

    @Test
    void pedidoAindaNaoEntregueNaoPedeTroca() {
        for (StatusPedido status : List.of(StatusPedido.PAGO, StatusPedido.ENVIADO, StatusPedido.CANCELADO)) {
            Pedido pedido = salvarPedido(status, null);

            assertThatThrownBy(() -> trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.DEFEITO)))
                    .isInstanceOf(ConflitoException.class)
                    .extracting("codigo").isEqualTo("PEDIDO_NAO_ENTREGUE");
        }
    }

    @Test
    void pedidoDeOutraClienteFicaInvisivel() {
        Pedido pedido = entregue(hoje());

        assertThatThrownBy(() -> trocaService.solicitar(outraCliente.getId(), pedir(pedido, MotivoTroca.DEFEITO)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void prazoValeAteOSetimoDiaDepoisDaEntrega() {
        Pedido noUltimoDia = entregue(hoje().minusDays(7));
        Pedido foraDoPrazo = entregue(hoje().minusDays(8));

        assertThat(trocaService.solicitar(cliente.getId(), pedir(noUltimoDia, MotivoTroca.NAO_SERVIU)).id()).isNotNull();
        assertThatThrownBy(() -> trocaService.solicitar(cliente.getId(), pedir(foraDoPrazo, MotivoTroca.DEFEITO)))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining(hoje().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .extracting("codigo").isEqualTo("TROCA_FORA_DO_PRAZO");
    }

    // --- Uma troca por peça ---

    @Test
    void naoDuplicaATrocaDaMesmaPeca() {
        Pedido pedido = entregue(hoje());
        trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.DEFEITO));

        assertThatThrownBy(() -> trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.NAO_SERVIU)))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("TROCA_JA_SOLICITADA");
    }

    @Test
    void trocaRecusadaLiberaPedirDeNovo() {
        Pedido pedido = entregue(hoje());
        TrocaDTO primeira = trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.DEFEITO));
        trocaService.atualizarStatus(primeira.id(), new TrocaStatusRequestDTO(StatusTroca.RECUSADA, "Sem defeito aparente"));

        TrocaDTO segunda = trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.NAO_SERVIU));

        assertThat(segunda.id()).isNotEqualTo(primeira.id());
        assertThat(trocaService.listar(cliente.getId())).extracting(TrocaDTO::id)
                .containsExactly(segunda.id(), primeira.id());
    }

    @Test
    void outraPecaDoMesmoPedidoTemATrocaDela() {
        Pedido pedido = entregue(hoje());
        adicionarItem(pedido, outroProduto, Tamanho.P, 1);
        trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.DEFEITO));

        TrocaDTO troca = trocaService.solicitar(cliente.getId(), new TrocaRequestDTO(pedido.getId(),
                outroProduto.getId(), Tamanho.P, null, MotivoTroca.NAO_SERVIU, null));

        assertThat(troca.nome()).isEqualTo("Vestido Aurora");
        assertThat(troca.quantidade()).isEqualTo(1);
    }

    // --- A peça ---

    @Test
    void pecaQueNaoEstaNoPedidoEhRecusada() {
        Pedido pedido = entregue(hoje());

        assertThatThrownBy(() -> trocaService.solicitar(cliente.getId(), new TrocaRequestDTO(pedido.getId(),
                outroProduto.getId(), null, null, MotivoTroca.DEFEITO, null)))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> trocaService.solicitar(cliente.getId(), new TrocaRequestDTO(pedido.getId(),
                produto.getId(), Tamanho.GG, null, MotivoTroca.DEFEITO, null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Essa peça não está no pedido");
    }

    @Test
    void quantidadeNaoPassaDaComprada() {
        Pedido pedido = entregue(hoje());

        assertThatThrownBy(() -> trocaService.solicitar(cliente.getId(), new TrocaRequestDTO(pedido.getId(),
                produto.getId(), Tamanho.M, 3, MotivoTroca.DEFEITO, null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("O pedido tem 2 unidades dessa peça");

        TrocaDTO troca = trocaService.solicitar(cliente.getId(), new TrocaRequestDTO(pedido.getId(),
                produto.getId(), Tamanho.M, 1, MotivoTroca.DEFEITO, "  Costura aberta na manga  "));
        assertThat(troca.quantidade()).isEqualTo(1);
        assertThat(troca.observacao()).isEqualTo("Costura aberta na manga");
    }

    @Test
    void mesmoProdutoEmDoisTamanhosPedeOTamanho() {
        Pedido pedido = entregue(hoje());
        adicionarItem(pedido, produto, Tamanho.G, 1);

        assertThatThrownBy(() -> trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.DEFEITO)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Informe o tamanho da peça");

        TrocaDTO troca = trocaService.solicitar(cliente.getId(), new TrocaRequestDTO(pedido.getId(),
                produto.getId(), Tamanho.G, null, MotivoTroca.DEFEITO, null));
        assertThat(troca.tamanho()).isEqualTo("G");
        assertThat(troca.quantidade()).isEqualTo(1);
    }

    // --- Status pelo painel ---

    @Test
    void roseaneAprovaEConcluiComRecadoParaACliente() {
        Pedido pedido = entregue(hoje());
        TrocaDTO troca = trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.DEFEITO));

        TrocaAdminDTO aprovada = trocaService.atualizarStatus(troca.id(),
                new TrocaStatusRequestDTO(StatusTroca.APROVADA, "Pode mandar pelos Correios"));
        assertThat(aprovada.status()).isEqualTo("APROVADA");
        assertThat(aprovada.cliente().email()).isEqualTo("troca-servico@rosie.test");

        TrocaAdminDTO concluida = trocaService.atualizarStatus(troca.id(), new TrocaStatusRequestDTO(StatusTroca.CONCLUIDA, " "));
        assertThat(concluida.status()).isEqualTo("CONCLUIDA");
        // Sem recado novo, fica o anterior
        assertThat(concluida.resposta()).isEqualTo("Pode mandar pelos Correios");
        assertThat(trocaService.listar(cliente.getId()).get(0).status()).isEqualTo("CONCLUIDA");
    }

    @Test
    void statusSoAndaPeloCaminhoCerto() {
        Pedido pedido = entregue(hoje());
        TrocaDTO troca = trocaService.solicitar(cliente.getId(), pedir(pedido, MotivoTroca.DEFEITO));

        assertThatThrownBy(() -> trocaService.atualizarStatus(troca.id(), new TrocaStatusRequestDTO(StatusTroca.CONCLUIDA, null)))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Uma troca solicitada não pode passar para concluída")
                .extracting("codigo").isEqualTo("STATUS_TROCA_INVALIDO");

        trocaService.atualizarStatus(troca.id(), new TrocaStatusRequestDTO(StatusTroca.RECUSADA, null));
        assertThatThrownBy(() -> trocaService.atualizarStatus(troca.id(), new TrocaStatusRequestDTO(StatusTroca.APROVADA, null)))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("STATUS_TROCA_INVALIDO");
    }

    @Test
    void trocaInexistenteResponde404() {
        assertThatThrownBy(() -> trocaService.atualizarStatus(-1L, new TrocaStatusRequestDTO(StatusTroca.APROVADA, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // --- Pedido entregue (painel) ---

    @Test
    void marcaComoEntregueHojeEAbreOPrazo() {
        Pedido pedido = salvarPedido(StatusPedido.ENVIADO, null);

        PedidoAdminDTO dto = pedidoAdminService.marcarEntregue(pedido.getId(), null);

        assertThat(dto.status()).isEqualTo("ENTREGUE");
        assertThat(dto.dataEntrega()).isEqualTo(hoje());
        PedidoResumoDTO resumo = pedidoService.listar(cliente.getId()).get(0);
        assertThat(resumo.prazoTroca()).isEqualTo(hoje().plusDays(7));
        assertThat(pedidoService.buscar(cliente.getId(), pedido.getId()).prazoTroca()).isEqualTo(hoje().plusDays(7));
    }

    @Test
    void marcaComADataInformadaERepetirNaoMudaAData() {
        Pedido pedido = salvarPedido(StatusPedido.ENVIADO, null);
        pedido.setEtiquetaGeradaEm(LocalDateTime.now().minusDays(5));

        pedidoAdminService.marcarEntregue(pedido.getId(), hoje().minusDays(3));
        PedidoAdminDTO repetido = pedidoAdminService.marcarEntregue(pedido.getId(), hoje());

        assertThat(repetido.status()).isEqualTo("ENTREGUE");
        assertThat(repetido.dataEntrega()).isEqualTo(hoje().minusDays(3));
    }

    @Test
    void soPedidoEnviadoViraEntregue() {
        for (StatusPedido status : List.of(StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.PAGO, StatusPedido.CANCELADO)) {
            Pedido pedido = salvarPedido(status, null);

            assertThatThrownBy(() -> pedidoAdminService.marcarEntregue(pedido.getId(), null))
                    .isInstanceOf(ConflitoException.class)
                    .extracting("codigo").isEqualTo("PEDIDO_NAO_ENVIADO");
        }
    }

    @Test
    void dataDeEntregaNoFuturoOuAntesDoEnvioEhRecusada() {
        Pedido pedido = salvarPedido(StatusPedido.ENVIADO, null);
        pedido.setEtiquetaGeradaEm(LocalDateTime.now().minusDays(2));

        assertThatThrownBy(() -> pedidoAdminService.marcarEntregue(pedido.getId(), hoje().plusDays(1)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("A data da entrega não pode ser no futuro");
        assertThatThrownBy(() -> pedidoAdminService.marcarEntregue(pedido.getId(), hoje().minusDays(10)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("A data da entrega não pode ser antes do envio");
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.ENVIADO);
    }

    @Test
    void pedidoNaoEntregueNaoTemPrazoDeTroca() {
        Pedido pedido = salvarPedido(StatusPedido.ENVIADO, null);

        assertThat(pedidoService.buscar(cliente.getId(), pedido.getId()).prazoTroca()).isNull();
    }

    private LocalDate hoje() {
        return prazoTroca.hoje();
    }

    private static TrocaRequestDTO pedir(Pedido pedido, MotivoTroca motivo) {
        Long produtoId = pedido.getItens().get(0).getProduto().getId();
        return new TrocaRequestDTO(pedido.getId(), produtoId, null, null, motivo, null);
    }

    private Pedido entregue(LocalDate dataEntrega) {
        return salvarPedido(StatusPedido.ENTREGUE, dataEntrega);
    }

    // Pedido com 2 unidades do Conjunto Serpente M
    private Pedido salvarPedido(StatusPedido status, LocalDate dataEntrega) {
        Pedido pedido = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(status).formaPagamento(FormaPagamento.PIX)
                .transportadora("Correios").servicoFrete("PAC").servicoFreteId(1).prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90")).valorTotal(new BigDecimal("400.90"))
                .dataEntrega(dataEntrega)
                .build();
        pedido.getItens().add(PedidoItem.builder().pedido(pedido).produto(produto).tamanho(Tamanho.M)
                .quantidade(2).precoUnitario(new BigDecimal("189.00")).build());
        return pedidoRepository.saveAndFlush(pedido);
    }

    private void adicionarItem(Pedido pedido, Produto doItem, Tamanho tamanho, int quantidade) {
        pedido.getItens().add(PedidoItem.builder().pedido(pedido).produto(doItem).tamanho(tamanho)
                .quantidade(quantidade).precoUnitario(doItem.getPreco()).build());
        pedidoRepository.saveAndFlush(pedido);
    }
}
