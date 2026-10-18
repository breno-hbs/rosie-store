package com.rosie.backend.service;

import com.rosie.backend.dto.EstoqueDTO;
import com.rosie.backend.dto.ProdutoAdminDTO;
import com.rosie.backend.dto.ProdutoAdminResumoDTO;
import com.rosie.backend.dto.ProdutoRequestDTO;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
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
    void atualizarTrocaDadosEEstoque() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Original", vestido, true, null,
                List.of(new EstoqueDTO("P", 3), new EstoqueDTO("M", 5))));
        // a edição chega em outra requisição, com o produto carregado do banco
        entityManager.flush();
        entityManager.clear();

        ProdutoAdminDTO editado = produtoAdminService.atualizar(criado.id(), new ProdutoRequestDTO(
                "Editado", "  ", new BigDecimal("150.50"), calca.getId(), null, null,
                List.of(new EstoqueDTO("M", 0), new EstoqueDTO("GG", 7))));

        assertThat(editado.nome()).isEqualTo("Editado");
        assertThat(editado.descricao()).isNull();
        assertThat(editado.preco()).isEqualByComparingTo("150.50");
        assertThat(editado.categoria().id()).isEqualTo(calca.getId());
        // destaque ausente no PUT mantém o valor atual
        assertThat(editado.destaque()).isTrue();
        // P saiu, M foi atualizado e GG entrou
        assertThat(editado.estoque()).containsExactly(new EstoqueDTO("M", 0), new EstoqueDTO("GG", 7));

        entityManager.clear();
        assertThat(produtoAdminService.buscarPorId(criado.id()).estoque())
                .containsExactly(new EstoqueDTO("M", 0), new EstoqueDTO("GG", 7));
    }

    @Test
    void atualizarDesmarcaDestaque() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Produto", vestido, true, null, null));

        ProdutoAdminDTO editado = produtoAdminService.atualizar(criado.id(), request("Produto", vestido, false, null, null));

        assertThat(editado.destaque()).isFalse();
    }

    @Test
    void atualizarProdutoInexistenteFalha() {
        assertThatThrownBy(() -> produtoAdminService.atualizar(-1L, request("Produto", vestido, false, null, null)))
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

    @Test
    void atualizarComAtivoTrueReativa() {
        ProdutoAdminDTO criado = produtoAdminService.criar(request("Produto", vestido, false, null, null));
        produtoAdminService.desativar(criado.id());

        ProdutoAdminDTO reativado = produtoAdminService.atualizar(criado.id(), request("Produto", vestido, null, true, null));

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

    private ProdutoRequestDTO request(String nome, Categoria categoria, Boolean destaque, Boolean ativo, List<EstoqueDTO> estoque) {
        return new ProdutoRequestDTO(nome, "Descrição de teste", new BigDecimal("189.00"), categoria.getId(),
                destaque, ativo, estoque);
    }
}
