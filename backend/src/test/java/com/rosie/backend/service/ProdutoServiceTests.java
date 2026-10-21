package com.rosie.backend.service;

import com.rosie.backend.dto.EstoqueDTO;
import com.rosie.backend.dto.ProdutoDetalheDTO;
import com.rosie.backend.dto.ProdutoResumoDTO;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Usa o MySQL local; @Transactional desfaz os inserts ao fim de cada teste
@SpringBootTest
@Transactional
class ProdutoServiceTests {

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Categoria categoria;
    private Produto ativo;
    private Produto inativo;

    @BeforeEach
    void setUp() {
        categoria = categoriaRepository.save(Categoria.builder()
                .nome("Teste " + System.nanoTime())
                .build());

        ativo = produtoRepository.save(Produto.builder()
                .categoria(categoria)
                .nome("Produto ativo")
                .preco(new BigDecimal("100.00"))
                .ativo(true)
                .build());

        inativo = produtoRepository.save(Produto.builder()
                .categoria(categoria)
                .nome("Produto inativo")
                .preco(new BigDecimal("100.00"))
                .ativo(false)
                .build());
    }

    @Test
    void listarSemCategoriaRetornaSoAtivos() {
        // tamanho grande para não depender dos produtos já existentes no banco
        List<Long> ids = idsDe(produtoService.listar(null, null, 0, 10_000).conteudo());

        assertThat(ids).contains(ativo.getId()).doesNotContain(inativo.getId());
    }

    @Test
    void listarPorCategoriaRetornaSoAtivos() {
        var resposta = produtoService.listar(categoria.getId(), null, 0, 12);

        assertThat(idsDe(resposta.conteudo())).containsExactly(ativo.getId());
        assertThat(resposta.totalItens()).isEqualTo(1);
    }

    @Test
    void buscarPorIdRetornaProdutoAtivo() {
        ProdutoDetalheDTO detalhe = produtoService.buscarPorId(ativo.getId());

        assertThat(detalhe.id()).isEqualTo(ativo.getId());
        assertThat(detalhe.nome()).isEqualTo("Produto ativo");
    }

    @Test
    void buscarPorIdDeProdutoInativoLancaNaoEncontrado() {
        assertThatThrownBy(() -> produtoService.buscarPorId(inativo.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void buscarPorIdOrdenaEstoquePorTamanho() {
        for (Tamanho tamanho : List.of(Tamanho.GG, Tamanho.P, Tamanho.G, Tamanho.M)) {
            ativo.getEstoque().add(ProdutoEstoque.builder()
                    .produto(ativo)
                    .tamanho(tamanho)
                    .quantidade(1)
                    .build());
        }
        produtoRepository.saveAndFlush(ativo);

        List<String> tamanhos = produtoService.buscarPorId(ativo.getId()).estoque().stream()
                .map(EstoqueDTO::tamanho)
                .toList();

        assertThat(tamanhos).containsExactly("P", "M", "G", "GG");
    }

    @Test
    void listarMarcaDisponivelSoComEstoque() {
        // ativo: P zerado e M com estoque; esgotado: só P zerado; semEstoque: nenhum tamanho cadastrado
        adicionarEstoque(ativo, Tamanho.P, 0);
        adicionarEstoque(ativo, Tamanho.M, 2);

        Produto esgotado = produtoRepository.save(Produto.builder()
                .categoria(categoria)
                .nome("Produto esgotado")
                .preco(new BigDecimal("100.00"))
                .ativo(true)
                .build());
        adicionarEstoque(esgotado, Tamanho.P, 0);

        Produto semEstoque = produtoRepository.saveAndFlush(Produto.builder()
                .categoria(categoria)
                .nome("Produto sem estoque")
                .preco(new BigDecimal("100.00"))
                .ativo(true)
                .build());

        Map<Long, Boolean> disponivelPorId = produtoService.listar(categoria.getId(), null, 0, 12).conteudo().stream()
                .collect(Collectors.toMap(ProdutoResumoDTO::id, ProdutoResumoDTO::disponivel));

        assertThat(disponivelPorId)
                .containsEntry(ativo.getId(), true)
                .containsEntry(esgotado.getId(), false)
                .containsEntry(semEstoque.getId(), false);
    }

    @Test
    void listarDestaquesRetornaSoAtivosMarcados() {
        Produto destaque = salvarProduto("Produto destaque", true, true);
        Produto destaqueInativo = salvarProduto("Produto destaque inativo", false, true);

        List<Long> ids = idsDe(produtoService.listarDestaques(ProdutoService.LIMITE_MAXIMO_DESTAQUES));

        assertThat(ids)
                .contains(destaque.getId())
                .doesNotContain(ativo.getId(), inativo.getId(), destaqueInativo.getId());
    }

    @Test
    void listarDestaquesRespeitaLimiteComMaisRecentesPrimeiro() {
        salvarProduto("Destaque 1", true, true);
        Produto segundo = salvarProduto("Destaque 2", true, true);
        Produto terceiro = salvarProduto("Destaque 3", true, true);

        List<Long> ids = idsDe(produtoService.listarDestaques(2));

        assertThat(ids).containsExactly(terceiro.getId(), segundo.getId());
    }

    @Test
    void listarDestaquesAjustaLimiteForaDoIntervalo() {
        salvarProduto("Destaque 1", true, true);
        salvarProduto("Destaque 2", true, true);

        assertThat(produtoService.listarDestaques(0)).hasSize(1);
        assertThat(produtoService.listarDestaques(10_000)).hasSizeLessThanOrEqualTo(ProdutoService.LIMITE_MAXIMO_DESTAQUES);
    }

    @Test
    void produtoNovoNaoEhDestaquePorPadrao() {
        assertThat(produtoRepository.findById(ativo.getId()).orElseThrow().getDestaque()).isFalse();
    }

    // Busca do header (#82). Cada busca leva uma marca única para não depender dos produtos já existentes no banco
    @Test
    void buscarProcuraEmNomeDescricaoEComposicaoComTodosOsTermos() {
        String marca = "marca" + System.nanoTime();
        Produto midi = salvarProduto("Vestido midi " + marca, null, null);
        Produto decote = salvarProduto("Blusa " + marca, "Decote canoa e manga curta", null);
        Produto linho = salvarProduto("Camisa " + marca, null, "100% linho. Lavar à mão");
        produtoRepository.flush();

        assertThat(idsDe(produtoService.listar(null, marca, 0, 12).conteudo()))
                .containsExactlyInAnyOrder(midi.getId(), decote.getId(), linho.getId());
        assertThat(idsDe(produtoService.listar(null, "vestido midi " + marca, 0, 12).conteudo()))
                .containsExactly(midi.getId());
        assertThat(idsDe(produtoService.listar(null, marca + " decote", 0, 12).conteudo()))
                .containsExactly(decote.getId());
        assertThat(idsDe(produtoService.listar(null, "  LINHO   " + marca + " ", 0, 12).conteudo()))
                .containsExactly(linho.getId());
        assertThat(produtoService.listar(null, marca + " seda", 0, 12).totalItens()).isZero();
    }

    @Test
    void buscarPeloNomeDaCategoriaSoTrazAtivos() {
        // categoria do setUp: "Teste <nanoTime>"; o número só aparece no nome dela
        String numero = categoria.getNome().substring("Teste ".length());

        var resposta = produtoService.listar(null, numero, 0, 12);

        assertThat(idsDe(resposta.conteudo())).containsExactly(ativo.getId());
        assertThat(resposta.totalItens()).isEqualTo(1);
    }

    @Test
    void buscarIgnoraMaiusculasEAcentos() {
        String marca = "marca" + System.nanoTime();
        Produto calca = salvarProduto("Calça pantalona " + marca, null, null);
        produtoRepository.flush();

        assertThat(idsDe(produtoService.listar(null, "CALCA " + marca, 0, 12).conteudo()))
                .containsExactly(calca.getId());
    }

    @Test
    void buscarComCategoriaCombinaOsDoisFiltros() {
        String marca = "marca" + System.nanoTime();
        Produto daCategoria = salvarProduto("Vestido " + marca, null, null);
        Categoria outra = categoriaRepository.save(Categoria.builder().nome("Outra " + System.nanoTime()).build());
        produtoRepository.saveAndFlush(Produto.builder()
                .categoria(outra)
                .nome("Vestido " + marca)
                .preco(new BigDecimal("100.00"))
                .build());

        assertThat(idsDe(produtoService.listar(categoria.getId(), marca, 0, 12).conteudo()))
                .containsExactly(daCategoria.getId());
    }

    @Test
    void buscarTrataCuringaDoLikeComoTexto() {
        // Sem escapar, "%" e "_" achariam qualquer produto da categoria
        assertThat(produtoService.listar(categoria.getId(), "%", 0, 12).totalItens()).isZero();
        assertThat(produtoService.listar(categoria.getId(), "_", 0, 12).totalItens()).isZero();
    }

    @Test
    void buscarVaziaListaComoSemBusca() {
        assertThat(idsDe(produtoService.listar(categoria.getId(), "   ", 0, 12).conteudo()))
                .containsExactly(ativo.getId());
    }

    @Test
    void termosDaBuscaCortaTamanhoERepeticao() {
        assertThat(ProdutoService.termosDaBusca(null)).isEmpty();
        assertThat(ProdutoService.termosDaBusca(" Vestido  vestido MIDI ")).containsExactly("vestido", "midi");
        assertThat(ProdutoService.termosDaBusca("a b c d e f g")).hasSize(ProdutoService.TERMOS_MAXIMOS_BUSCA);
        assertThat(ProdutoService.termosDaBusca("x".repeat(500)).get(0)).hasSize(ProdutoService.TAMANHO_MAXIMO_BUSCA);
    }

    private Produto salvarProduto(String nome, String descricao, String composicao) {
        return produtoRepository.save(Produto.builder()
                .categoria(categoria)
                .nome(nome)
                .descricao(descricao)
                .composicao(composicao)
                .preco(new BigDecimal("100.00"))
                .build());
    }

    private Produto salvarProduto(String nome, boolean ativo, boolean destaque) {
        return produtoRepository.save(Produto.builder()
                .categoria(categoria)
                .nome(nome)
                .preco(new BigDecimal("100.00"))
                .ativo(ativo)
                .destaque(destaque)
                .build());
    }

    private void adicionarEstoque(Produto produto, Tamanho tamanho, int quantidade) {
        produto.getEstoque().add(ProdutoEstoque.builder()
                .produto(produto)
                .tamanho(tamanho)
                .quantidade(quantidade)
                .build());
        produtoRepository.saveAndFlush(produto);
    }

    private List<Long> idsDe(List<ProdutoResumoDTO> conteudo) {
        return conteudo.stream().map(ProdutoResumoDTO::id).toList();
    }
}
