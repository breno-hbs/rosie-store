package com.rosie.backend.service;

import com.rosie.backend.dto.CategoriaAdminDTO;
import com.rosie.backend.dto.CategoriaResumoDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.CategoriaSlugAntigoRepository;
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

    @Autowired
    private CategoriaSlugAntigoRepository slugAntigoRepository;

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
                new CategoriaAdminDTO(comProdutos.getId(), comProdutos.getNome(), comProdutos.getSlug(), 2),
                new CategoriaAdminDTO(vazia.getId(), vazia.getNome(), vazia.getSlug(), 0));
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

        assertThat(renomeada).isEqualTo(new CategoriaAdminDTO(categoria.getId(), "Novo " + sufixo, "novo-" + sufixo, 1));
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

    @Test
    void criarGeraOSlugDoNome() {
        CategoriaAdminDTO criada = categoriaService.criar("Cropped/Body Ávila " + sufixo);

        assertThat(criada.slug()).isEqualTo("cropped-body-avila-" + sufixo);
        assertThat(categoriaRepository.findById(criada.id())).get()
                .extracting(Categoria::getSlug).isEqualTo("cropped-body-avila-" + sufixo);
    }

    @Test
    void criarComSlugJaUsadoGanhaSufixo() {
        CategoriaAdminDTO primeira = categoriaService.criar("Saia Longa " + sufixo);
        CategoriaAdminDTO segunda = categoriaService.criar("Saia/Longa " + sufixo);
        CategoriaAdminDTO terceira = categoriaService.criar("Saia - Longa " + sufixo);

        assertThat(primeira.slug()).isEqualTo("saia-longa-" + sufixo);
        assertThat(segunda.slug()).isEqualTo("saia-longa-" + sufixo + "-2");
        assertThat(terceira.slug()).isEqualTo("saia-longa-" + sufixo + "-3");
    }

    @Test
    void renomearTrocaOSlugEOAntigoContinuaAchandoACategoria() {
        CategoriaAdminDTO criada = categoriaService.criar("Vestido " + sufixo);

        CategoriaAdminDTO renomeada = categoriaService.renomear(criada.id(), "Vestidos de Festa " + sufixo);

        assertThat(renomeada.slug()).isEqualTo("vestidos-de-festa-" + sufixo);
        CategoriaResumoDTO esperada = new CategoriaResumoDTO(criada.id(), "Vestidos de Festa " + sufixo,
                "vestidos-de-festa-" + sufixo);
        assertThat(categoriaService.buscarPorSlug("vestidos-de-festa-" + sufixo)).isEqualTo(esperada);
        assertThat(categoriaService.buscarPorSlug("vestido-" + sufixo)).isEqualTo(esperada);
    }

    @Test
    void renomearSoMudandoMaiusculasMantemOSlug() {
        CategoriaAdminDTO criada = categoriaService.criar("Body " + sufixo);
        CategoriaAdminDTO outra = categoriaService.criar("Body! " + sufixo);

        CategoriaAdminDTO renomeada = categoriaService.renomear(outra.id(), "BODY! " + sufixo);

        assertThat(criada.slug()).isEqualTo("body-" + sufixo);
        assertThat(renomeada.slug()).isEqualTo("body-" + sufixo + "-2");
        assertThat(slugAntigoRepository.findBySlug("body-" + sufixo + "-2")).isEmpty();
    }

    @Test
    void renomearDeVoltaRecuperaOSlugSemDeixarRegistroRepetido() {
        CategoriaAdminDTO criada = categoriaService.criar("Short " + sufixo);
        categoriaService.renomear(criada.id(), "Bermuda " + sufixo);

        CategoriaAdminDTO devolta = categoriaService.renomear(criada.id(), "Short " + sufixo);

        assertThat(devolta.slug()).isEqualTo("short-" + sufixo);
        assertThat(slugAntigoRepository.findBySlug("short-" + sufixo)).isEmpty();
        assertThat(slugAntigoRepository.findBySlug("bermuda-" + sufixo)).get()
                .extracting(antigo -> antigo.getCategoria().getId()).isEqualTo(criada.id());
    }

    @Test
    void categoriaNovaFicaComOSlugAntigoDeOutra() {
        CategoriaAdminDTO antiga = categoriaService.criar("Macacão " + sufixo);
        categoriaService.renomear(antiga.id(), "Macaquinho " + sufixo);

        CategoriaAdminDTO nova = categoriaService.criar("Macacão " + sufixo);

        assertThat(nova.slug()).isEqualTo("macacao-" + sufixo);
        assertThat(categoriaService.buscarPorSlug("macacao-" + sufixo).id()).isEqualTo(nova.id());
        assertThat(categoriaService.buscarPorSlug("macaquinho-" + sufixo).id()).isEqualTo(antiga.id());
    }

    @Test
    void removerLevaOsSlugsAntigosJunto() {
        CategoriaAdminDTO criada = categoriaService.criar("Pijama " + sufixo);
        categoriaService.renomear(criada.id(), "Pijamas " + sufixo);

        categoriaService.remover(criada.id());

        assertThat(slugAntigoRepository.findBySlug("pijama-" + sufixo)).isEmpty();
        assertThatThrownBy(() -> categoriaService.buscarPorSlug("pijama-" + sufixo))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void buscarPorSlugInexistenteDa404() {
        assertThatThrownBy(() -> categoriaService.buscarPorSlug("nao-existe-" + sufixo))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Categoria não encontrada");
    }

    @Test
    void categoriaSalvaDiretoPeloRepositoryGanhaSlugDoNome() {
        Categoria categoria = novaCategoria("Calça Jeans " + sufixo);

        assertThat(categoria.getSlug()).isEqualTo("calca-jeans-" + sufixo);
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
