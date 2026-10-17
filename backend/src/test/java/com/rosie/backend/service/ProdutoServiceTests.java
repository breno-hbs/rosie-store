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
        List<Long> ids = idsDe(produtoService.listar(null, 0, 10_000).conteudo());

        assertThat(ids).contains(ativo.getId()).doesNotContain(inativo.getId());
    }

    @Test
    void listarPorCategoriaRetornaSoAtivos() {
        var resposta = produtoService.listar(categoria.getId(), 0, 12);

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

        Map<Long, Boolean> disponivelPorId = produtoService.listar(categoria.getId(), 0, 12).conteudo().stream()
                .collect(Collectors.toMap(ProdutoResumoDTO::id, ProdutoResumoDTO::disponivel));

        assertThat(disponivelPorId)
                .containsEntry(ativo.getId(), true)
                .containsEntry(esgotado.getId(), false)
                .containsEntry(semEstoque.getId(), false);
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
