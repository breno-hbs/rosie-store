package com.rosie.backend.service;

import com.rosie.backend.dto.CategoriaAdminDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Usa o MySQL local; @Transactional desfaz os inserts ao fim de cada teste
@SpringBootTest
@Transactional
class CategoriaServiceTests {

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    // Sufixo único para não colidir com as categorias que já existem no banco
    private String sufixo;

    @BeforeEach
    void setUp() {
        sufixo = String.valueOf(System.nanoTime());
    }

    @Test
    void criarTiraEspacosDasPontasESalva() {
        CategoriaAdminDTO criada = categoriaService.criar("  Acessórios " + sufixo + "  ");

        assertThat(criada.id()).isNotNull();
        assertThat(criada.nome()).isEqualTo("Acessórios " + sufixo);
        assertThat(criada.quantidadeProdutos()).isZero();
        assertThat(categoriaRepository.findById(criada.id())).get()
                .extracting(Categoria::getNome).isEqualTo("Acessórios " + sufixo);
    }

    @Test
    void criarComNomeRepetidoSemDiferenciarMaiusculasDaConflito() {
        categoriaService.criar("Bolsas " + sufixo);

        assertThatThrownBy(() -> categoriaService.criar(" BOLSAS " + sufixo))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Já existe uma categoria com esse nome")
                .extracting("codigo").isEqualTo("CATEGORIA_DUPLICADA");
    }

    @Test
    void listarAdminTrazQuantidadeDeProdutosAtivosEInativos() {
        Categoria comProdutos = novaCategoria("Com produtos " + sufixo);
        Categoria vazia = novaCategoria("Vazia " + sufixo);
        novoProduto(comProdutos, true);
        novoProduto(comProdutos, false);

        var lista = categoriaService.listarAdmin();

        assertThat(lista).contains(
                new CategoriaAdminDTO(comProdutos.getId(), comProdutos.getNome(), 2),
                new CategoriaAdminDTO(vazia.getId(), vazia.getNome(), 0));
    }

    @Test
    void removerCategoriaVaziaApaga() {
        Categoria vazia = novaCategoria("Vazia " + sufixo);

        categoriaService.remover(vazia.getId());

        assertThat(categoriaRepository.findById(vazia.getId())).isEmpty();
    }

    @Test
    void removerCategoriaComProdutoInativoTambemEBloqueado() {
        Categoria categoria = novaCategoria("Em uso " + sufixo);
        novoProduto(categoria, false);

        assertThatThrownBy(() -> categoriaService.remover(categoria.getId()))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Não dá para remover \"Em uso " + sufixo
                        + "\": a categoria tem 1 produto. Mova os produtos para outra categoria antes.")
                .extracting("codigo").isEqualTo("CATEGORIA_EM_USO");
        assertThat(categoriaRepository.findById(categoria.getId())).isPresent();
    }

    @Test
    void removerCategoriaInexistenteDa404() {
        assertThatThrownBy(() -> categoriaService.remover(-1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Categoria não encontrada");
    }

    @Test
    void renomearTrocaONomeEMantemAQuantidade() {
        Categoria categoria = novaCategoria("Antigo " + sufixo);
        novoProduto(categoria, true);

        CategoriaAdminDTO renomeada = categoriaService.renomear(categoria.getId(), " Novo " + sufixo + " ");

        assertThat(renomeada).isEqualTo(new CategoriaAdminDTO(categoria.getId(), "Novo " + sufixo, 1));
    }

    @Test
    void renomearSoMudandoMaiusculasDaPropriaCategoriaPode() {
        Categoria categoria = novaCategoria("cropped " + sufixo);

        CategoriaAdminDTO renomeada = categoriaService.renomear(categoria.getId(), "Cropped " + sufixo);

        assertThat(renomeada.nome()).isEqualTo("Cropped " + sufixo);
    }

    @Test
    void renomearParaNomeDeOutraCategoriaDaConflito() {
        novaCategoria("Saia " + sufixo);
        Categoria outra = novaCategoria("Short " + sufixo);

        assertThatThrownBy(() -> categoriaService.renomear(outra.getId(), "saia " + sufixo))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("CATEGORIA_DUPLICADA");
    }

    @Test
    void renomearCategoriaInexistenteDa404() {
        assertThatThrownBy(() -> categoriaService.renomear(-1L, "Qualquer " + sufixo))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private Categoria novaCategoria(String nome) {
        return categoriaRepository.save(Categoria.builder().nome(nome).build());
    }

    private void novoProduto(Categoria categoria, boolean ativo) {
        produtoRepository.save(Produto.builder()
                .categoria(categoria)
                .nome("Produto " + (ativo ? "ativo" : "inativo"))
                .preco(new BigDecimal("100.00"))
                .ativo(ativo)
                .build());
    }
}
