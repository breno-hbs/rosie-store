package com.rosie.backend.service;

import com.rosie.backend.dto.EstoqueDTO;
import com.rosie.backend.dto.PacoteDTO;
import com.rosie.backend.dto.ProdutoAdminDTO;
import com.rosie.backend.dto.ProdutoAdminResumoDTO;
import com.rosie.backend.dto.ProdutoEdicaoDTO;
import com.rosie.backend.dto.ProdutoRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Endereco;
import com.rosie.backend.model.FormaPagamento;
import com.rosie.backend.model.MotivoTroca;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.PedidoItem;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.StatusPedido;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Troca;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.EnderecoRepository;
import com.rosie.backend.repository.PedidoRepository;
import com.rosie.backend.repository.ProdutoEstoqueRepository;
import com.rosie.backend.repository.ProdutoImagemRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.TrocaRepository;
import com.rosie.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Usa o MySQL local; @Transactional desfaz os inserts ao fim de cada teste
@SpringBootTest
@Transactional
class ProdutoAdminServiceTests {

    @Autowired
    private ProdutoAdminService produtoAdminService;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoEstoqueRepository produtoEstoqueRepository;

    @Autowired
    private ProdutoImagemRepository produtoImagemRepository;

    @Autowired
    private CarrinhoItemRepository carrinhoItemRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TrocaRepository trocaRepository;

    @Autowired
    private EntityManager entityManager;

    private Categoria vestido;
    private Categoria calca;

    @BeforeEach
    void setUp() {
        vestido = categoriaRepository.save(Categoria.builder().nome("Vestido teste " + System.nanoTime()).build());
        calca = categoriaRepository.save(Categoria.builder().nome("Calça teste " + System.nanoTime()).build());
    }

    @Test
    void criarSalvaProdutoAtivoComEstoqueEDestaque() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("  Conjunto Serpente  ", vestido, true, null,
                List.of(new EstoqueDTO("M", 5), new EstoqueDTO("P", 3))));

        assertThat(criado.id()).isNotNull();
        assertThat(criado.nome()).isEqualTo("Conjunto Serpente");
        assertThat(criado.categoria().id()).isEqualTo(vestido.getId());
        assertThat(criado.ativo()).isTrue();
        assertThat(criado.destaque()).isTrue();
        assertThat(criado.estoque()).containsExactly(new EstoqueDTO("P", 3), new EstoqueDTO("M", 5));

        // aparece na vitrine e nos destaques
        assertThat(produtoService.buscarPorId(criado.id()).nome()).isEqualTo("Conjunto Serpente");
        assertThat(produtoService.listarDestaques(ProdutoService.LIMITE_MAXIMO_DESTAQUES))
                .anyMatch(p -> p.id().equals(criado.id()));
    }

    @Test
    void criarSemDestaqueEAtivoUsaPadroes() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Calça Linho", calca, null, null, null));

        assertThat(criado.ativo()).isTrue();
        assertThat(criado.destaque()).isFalse();
        assertThat(criado.estoque()).isEmpty();
        assertThat(criado.descricao()).isEqualTo("Descrição de teste");
    }

    @Test
    void criarComCategoriaInexistenteFalha() {
        Categoria inexistente = Categoria.builder().id(-1L).build();

        assertThatThrownBy(() -> produtoAdminService.criar(request("Produto", inexistente, false, null, null)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Categoria não encontrada");
    }

    @Test
    void criarComTamanhoRepetidoFalha() {
        var estoque = List.of(new EstoqueDTO("P", 1), new EstoqueDTO("P", 2));

        assertThatThrownBy(() -> produtoAdminService.criar(request("Produto", vestido, false, null, estoque)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Tamanho P repetido no estoque");
    }

    @Test
    void atualizarTrocaDadosSemMexerNoEstoque() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Original", vestido, true, null,
                List.of(new EstoqueDTO("P", 3), new EstoqueDTO("M", 5))));
        // a edição chega em outra requisição, com o produto carregado do banco
        entityManager.flush();
        entityManager.clear();

        ProdutoAdminDTO editado = produtoAdminService.atualizar(criado.id(), new ProdutoEdicaoDTO(
                "Editado", "  ", null, new BigDecimal("150.50"), calca.getId(), null, null, null));

        assertThat(editado.nome()).isEqualTo("Editado");
        assertThat(editado.descricao()).isNull();
        assertThat(editado.preco()).isEqualByComparingTo("150.50");
        assertThat(editado.categoria().id()).isEqualTo(calca.getId());
        // destaque ausente no PUT mantém o valor atual
        assertThat(editado.destaque()).isTrue();
        assertThat(editado.estoque()).containsExactly(new EstoqueDTO("P", 3), new EstoqueDTO("M", 5));

        entityManager.flush();
        entityManager.clear();
        assertThat(produtoAdminService.buscarPorId(criado.id()).estoque())
                .containsExactly(new EstoqueDTO("P", 3), new EstoqueDTO("M", 5));
    }

    @Test
    void atualizarNaoDesfazEstoqueAlteradoDepoisDeAbrirAEdicao() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Produto", vestido, false, null,
                List.of(new EstoqueDTO("P", 3), new EstoqueDTO("GG", 0))));
        entityManager.flush();
        entityManager.clear();

        // com a edição aberta, a tela de estoque esgota o P e repõe o GG
        produtoAdminService.atualizarEstoque(criado.id(), List.of(new EstoqueDTO("P", 0), new EstoqueDTO("GG", 2)));
        entityManager.flush();
        entityManager.clear();

        // a edição, aberta antes, salva os outros campos
        produtoAdminService.atualizar(criado.id(), edicao(vestido, false, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(produtoAdminService.buscarPorId(criado.id()).estoque())
                .containsExactly(new EstoqueDTO("P", 0), new EstoqueDTO("GG", 2));
    }

    @Test
    void atualizarDesmarcaDestaque() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Produto", vestido, true, null, null));

        ProdutoAdminDTO editado = produtoAdminService.atualizar(criado.id(), edicao(vestido, false, null));

        assertThat(editado.destaque()).isFalse();
    }

    @Test
    void atualizarProdutoInexistenteFalha() {
        assertThatThrownBy(() -> produtoAdminService.atualizar(-1L, edicao(vestido, false, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void desativarTiraDaVitrineSemApagar() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Produto", vestido, true, null, null));

        produtoAdminService.desativar(criado.id());
        entityManager.flush();
        entityManager.clear();

        Produto noBanco = produtoRepository.findById(criado.id()).orElseThrow();
        assertThat(noBanco.getAtivo()).isFalse();
        assertThat(produtoAdminService.buscarPorId(criado.id()).ativo()).isFalse();
        assertThatThrownBy(() -> produtoService.buscarPorId(criado.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(produtoService.listarDestaques(ProdutoService.LIMITE_MAXIMO_DESTAQUES))
                .noneMatch(p -> p.id().equals(criado.id()));
    }

    @Test
    void desativarProdutoInexistenteFalha() {
        assertThatThrownBy(() -> produtoAdminService.desativar(-1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // As fotos saem do armazenamento só depois do commit: ver ProdutoExclusaoTests
    @Test
    void excluirDefinitivamenteSemHistoricoApagaEstoqueFotosESacolas() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Cadastro errado", vestido, true, null,
                List.of(new EstoqueDTO("P", 2), new EstoqueDTO("M", 1))));
        Produto produto = produtoRepository.findById(criado.id()).orElseThrow();
        produto.getImagens().add(ProdutoImagem.builder().produto(produto).url("https://exemplo.test/1.jpg")
                .publicId("rosie/produtos/errado-1").ordem(0).build());
        ProdutoAdminDTO outro = produtoAdminService.criar(request("Fica", vestido, false, null,
                List.of(new EstoqueDTO("P", 2))));
        Usuario cliente = cliente();
        noCarrinho(cliente, criado.id());
        noCarrinho(cliente, outro.id());
        entityManager.flush();
        entityManager.clear();

        produtoAdminService.excluirDefinitivamente(criado.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(produtoRepository.findById(criado.id())).isEmpty();
        assertThat(produtoEstoqueRepository.findByProdutoId(criado.id())).isEmpty();
        assertThat(produtoImagemRepository.findByProdutoIdOrderByOrdemAsc(criado.id())).isEmpty();
        // Sai da sacola da cliente; o outro produto continua nela
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId()))
                .extracting(item -> item.getProduto().getId())
                .containsExactly(outro.id());
        assertThat(produtoAdminService.listar()).noneMatch(p -> p.id().equals(criado.id()));
        assertThatThrownBy(() -> produtoService.buscarPorId(criado.id()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void excluirDefinitivamenteProdutoQueEntrouEmPedidoBloqueia() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Vendido", vestido, false, null, null));
        pedidoCom(cliente(), produtoRepository.getReferenceById(criado.id()));

        assertThatThrownBy(() -> produtoAdminService.excluirDefinitivamente(criado.id()))
                .isInstanceOf(ConflitoException.class)
                .hasMessage(ProdutoAdminService.MENSAGEM_COM_HISTORICO)
                .extracting("codigo").isEqualTo("PRODUTO_COM_HISTORICO");
        assertThat(produtoRepository.findById(criado.id())).isPresent();
    }

    @Test
    void excluirDefinitivamenteProdutoComTrocaBloqueia() {
        ProdutoAdminDTO vendido = produtoAdminService.criar(request("Vendido", vestido, false, null, null));
        ProdutoAdminDTO trocado = produtoAdminService.criar(request("Trocado", vestido, false, null, null));
        Pedido pedido = pedidoCom(cliente(), produtoRepository.getReferenceById(vendido.id()));
        // A troca sozinha já segura: aqui o produto dela nem está entre os itens do pedido
        trocaRepository.saveAndFlush(Troca.builder().pedido(pedido)
                .produto(produtoRepository.getReferenceById(trocado.id()))
                .tamanho(Tamanho.M).quantidade(1).motivo(MotivoTroca.NAO_SERVIU).build());

        assertThatThrownBy(() -> produtoAdminService.excluirDefinitivamente(trocado.id()))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("PRODUTO_COM_HISTORICO");
        assertThat(produtoRepository.findById(trocado.id())).isPresent();
    }

    @Test
    void excluirDefinitivamenteProdutoInexistenteFalha() {
        assertThatThrownBy(() -> produtoAdminService.excluirDefinitivamente(-1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void atualizarComAtivoTrueReativa() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Produto", vestido, false, null, null));
        produtoAdminService.desativar(criado.id());

        ProdutoAdminDTO reativado = produtoAdminService.atualizar(criado.id(), edicao(vestido, null, true));

        assertThat(reativado.ativo()).isTrue();
        assertThat(produtoService.buscarPorId(criado.id()).id()).isEqualTo(criado.id());
    }

    @Test
    void listarIncluiInativosComEstoqueTotal() {
        ProdutoAdminDTO ativo = produtoAdminService.criar(request("Ativo", vestido, false, null,
                List.of(new EstoqueDTO("P", 3), new EstoqueDTO("G", 9))));
        ProdutoAdminDTO inativo = produtoAdminService.criar(request("Inativo", calca, false, false, null));
        entityManager.flush();
        entityManager.clear();

        List<ProdutoAdminResumoDTO> lista = produtoAdminService.listar();

        ProdutoAdminResumoDTO linhaAtivo = lista.stream().filter(p -> p.id().equals(ativo.id())).findFirst().orElseThrow();
        ProdutoAdminResumoDTO linhaInativo = lista.stream().filter(p -> p.id().equals(inativo.id())).findFirst().orElseThrow();
        assertThat(linhaAtivo.estoqueTotal()).isEqualTo(12);
        assertThat(linhaAtivo.categoria().nome()).isEqualTo(vestido.getNome());
        assertThat(linhaInativo.ativo()).isFalse();
        assertThat(linhaInativo.estoqueTotal()).isZero();
        // mais recente primeiro
        assertThat(lista.indexOf(linhaInativo)).isLessThan(lista.indexOf(linhaAtivo));
    }

    @Test
    void composicaoESalvaELidaNoPainelENaVitrine() {
        String composicao = "100% linho\nLavar à mão em água fria\nNão usar secadora";

        ProdutoAdminDTO criado = produtoAdminService.criar(requestComComposicao("  " + composicao + "  "));
        entityManager.flush();
        entityManager.clear();

        assertThat(criado.composicao()).isEqualTo(composicao);
        assertThat(produtoAdminService.buscarPorId(criado.id()).composicao()).isEqualTo(composicao);
        // as quebras de linha chegam na Página de Produto
        assertThat(produtoService.buscarPorId(criado.id()).composicao()).isEqualTo(composicao);
    }

    @Test
    void composicaoEOpcional() {
        ProdutoAdminDTO semCampo = produtoAdminService.criar(request("Sem composição", vestido, false, null, null));
        ProdutoAdminDTO emBranco = produtoAdminService.criar(requestComComposicao("   "));

        assertThat(semCampo.composicao()).isNull();
        assertThat(emBranco.composicao()).isNull();
        assertThat(produtoService.buscarPorId(semCampo.id()).composicao()).isNull();
    }

    @Test
    void atualizarComComposicaoVaziaLimpaOCampo() {
        ProdutoAdminDTO criado = produtoAdminService.criar(requestComComposicao("Viscose"));
        entityManager.flush();
        entityManager.clear();

        ProdutoAdminDTO editado = produtoAdminService.atualizar(criado.id(), requestComComposicao("").dadosDoProduto());

        assertThat(editado.composicao()).isNull();
    }

    @Test
    void atualizarSemDescricaoEComposicaoMantemOsTextos() {
        ProdutoAdminDTO criado = produtoAdminService.criar(requestComComposicao("Viscose"));
        entityManager.flush();
        entityManager.clear();

        // o mesmo que a reativação mandava antes do #50: sem composicao
        ProdutoAdminDTO editado = produtoAdminService.atualizar(criado.id(), new ProdutoEdicaoDTO(
                "Vestido Linho", null, null, new BigDecimal("189.00"), vestido.getId(), null, true, null));

        assertThat(editado.descricao()).isEqualTo("Descrição de teste");
        assertThat(editado.composicao()).isEqualTo("Viscose");
    }

    @Test
    void criarGuardaOPacoteDoFreteEEdicaoSemPacoteMantem() {
        ProdutoAdminDTO criado = produtoAdminService.criar(requestComPacote(
                new PacoteDTO(new BigDecimal("0.850"), 8, 25, 35)));
        entityManager.flush();
        entityManager.clear();

        assertThat(criado.pacote()).isEqualTo(new PacoteDTO(new BigDecimal("0.850"), 8, 25, 35));

        // o Reativar da lista não manda o pacote
        ProdutoAdminDTO editado = produtoAdminService.atualizar(criado.id(), new ProdutoEdicaoDTO(
                "Calça Jeans", null, null, new BigDecimal("149.90"), calca.getId(), null, true, null));

        assertThat(editado.pacote().pesoKg()).isEqualByComparingTo("0.85");
        assertThat(editado.pacote().alturaCm()).isEqualTo(8);
        assertThat(editado.pacote().larguraCm()).isEqualTo(25);
        assertThat(editado.pacote().comprimentoCm()).isEqualTo(35);
    }

    @Test
    void atualizarComPacoteTrocaOsQuatroCamposENuloVoltaParaOPadrao() {
        ProdutoAdminDTO criado = produtoAdminService.criar(requestComPacote(
                new PacoteDTO(new BigDecimal("0.85"), 8, 25, 35)));
        entityManager.flush();
        entityManager.clear();

        ProdutoAdminDTO editado = produtoAdminService.atualizar(criado.id(), new ProdutoEdicaoDTO(
                "Calça Jeans", null, null, new BigDecimal("149.90"), calca.getId(), null, null,
                new PacoteDTO(new BigDecimal("1.1"), null, null, 40)));

        assertThat(editado.pacote()).isEqualTo(new PacoteDTO(new BigDecimal("1.1"), null, null, 40));
    }

    @Test
    void colunasDoPacoteAceitamNulo() {
        // produtos cadastrados antes das colunas existirem ficam com null e usam o pacote padrão
        List<Object> nulaveis = entityManager.createNativeQuery("""
                SELECT IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'produto'
                  AND COLUMN_NAME IN ('peso_kg', 'altura_cm', 'largura_cm', 'comprimento_cm')
                """).getResultList();

        assertThat(nulaveis).hasSize(4).containsOnly("YES");
    }

    @Test
    void colunaComposicaoAceitaNulo() {
        // produtos cadastrados antes da coluna existir ficam com null
        Object nulavel = entityManager.createNativeQuery("""
                SELECT IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'produto' AND COLUMN_NAME = 'composicao'
                """).getSingleResult();

        assertThat(nulavel).isEqualTo("YES");
    }

    private Usuario cliente() {
        return usuarioRepository.save(Usuario.builder().nome("Cliente Teste")
                .email("exclusao-" + System.nanoTime() + "@rosie.test")
                .senhaHash("hash-irrelevante").role(Role.CLIENTE).build());
    }

    private void noCarrinho(Usuario cliente, Long produtoId) {
        carrinhoItemRepository.save(CarrinhoItem.builder().usuario(cliente)
                .produto(produtoRepository.getReferenceById(produtoId)).tamanho(Tamanho.P).quantidade(1).build());
    }

    // Até um pedido cancelado conta como histórico: ele continua no painel apontando para o produto
    private Pedido pedidoCom(Usuario cliente, Produto produto) {
        Endereco endereco = enderecoRepository.save(Endereco.builder()
                .usuario(cliente).cep("03925050").logradouro("Rua das Flores").numero("120")
                .bairro("Jardim Iva").cidade("São Paulo").estado("SP").build());
        Pedido pedido = Pedido.builder()
                .usuario(cliente).endereco(endereco).status(StatusPedido.CANCELADO).formaPagamento(FormaPagamento.PIX)
                .transportadora("Correios").servicoFrete("PAC").servicoFreteId(1).prazoFreteDias(7)
                .valorFrete(new BigDecimal("22.90")).valorTotal(new BigDecimal("211.90"))
                .build();
        pedido.getItens().add(PedidoItem.builder().pedido(pedido).produto(produto).tamanho(Tamanho.M)
                .quantidade(1).precoUnitario(new BigDecimal("189.00")).build());
        return pedidoRepository.saveAndFlush(pedido);
    }

    private ProdutoRequestDTO request(String nome, Categoria categoria, Boolean destaque, Boolean ativo, List<EstoqueDTO> estoque) {
        return new ProdutoRequestDTO(nome, "Descrição de teste", null, new BigDecimal("189.00"), categoria.getId(),
                destaque, ativo, estoque, null);
    }

    private ProdutoEdicaoDTO edicao(Categoria categoria, Boolean destaque, Boolean ativo) {
        return new ProdutoEdicaoDTO("Produto", "Descrição de teste", null, new BigDecimal("189.00"), categoria.getId(),
                destaque, ativo, null);
    }

    private ProdutoRequestDTO requestComPacote(PacoteDTO pacote) {
        return new ProdutoRequestDTO("Calça Jeans", null, null, new BigDecimal("149.90"), calca.getId(), null, null,
                null, pacote);
    }

    private ProdutoRequestDTO requestComComposicao(String composicao) {
        return new ProdutoRequestDTO("Vestido Linho", "Descrição de teste", composicao, new BigDecimal("189.00"),
                vestido.getId(), null, null, null, null);
    }
}
