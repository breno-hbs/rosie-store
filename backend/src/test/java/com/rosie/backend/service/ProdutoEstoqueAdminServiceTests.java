package com.rosie.backend.service;

import com.rosie.backend.dto.EstoqueDTO;
import com.rosie.backend.dto.EstoqueProdutoDTO;
import com.rosie.backend.dto.ProdutoResumoDTO;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Tamanho;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Gestão de estoque por tamanho (RF16) contra o MySQL local; @Transactional desfaz tudo no fim
@SpringBootTest
@Transactional
class ProdutoEstoqueAdminServiceTests {

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

    private Categoria categoria;

    @BeforeEach
    void setUp() {
        categoria = categoriaRepository.save(Categoria.builder().nome("Vestido estoque " + System.nanoTime()).build());
    }

    @Test
    void atualizaSoOsTamanhosEnviadosEDevolveOEstoqueCompletoOrdenado() {
        Produto produto = produtoComEstoque(true, Map.of(Tamanho.P, 3, Tamanho.M, 5));

        EstoqueProdutoDTO resposta = produtoAdminService.atualizarEstoque(produto.getId(),
                List.of(new EstoqueDTO("GG", 7), new EstoqueDTO("M", 0)));

        // P ficou como estava, M foi atualizado e GG entrou
        assertThat(resposta.produtoId()).isEqualTo(produto.getId());
        assertThat(resposta.estoque()).containsExactly(
                new EstoqueDTO("P", 3), new EstoqueDTO("M", 0), new EstoqueDTO("GG", 7));

        entityManager.clear();
        assertThat(produtoAdminService.buscarPorId(produto.getId()).estoque()).containsExactly(
                new EstoqueDTO("P", 3), new EstoqueDTO("M", 0), new EstoqueDTO("GG", 7));
    }

    @Test
    void zerarTodosOsTamanhosDeixaOProdutoIndisponivelNaVitrine() {
        Produto produto = produtoComEstoque(true, Map.of(Tamanho.P, 3, Tamanho.M, 5));
        assertThat(resumoNaVitrine(produto).disponivel()).isTrue();

        produtoAdminService.atualizarEstoque(produto.getId(),
                List.of(new EstoqueDTO("P", 0), new EstoqueDTO("M", 0)));
        entityManager.clear();

        assertThat(resumoNaVitrine(produto).disponivel()).isFalse();
        // continua na vitrine, só esgotado
        assertThat(produtoService.buscarPorId(produto.getId()).estoque())
                .allMatch(item -> item.quantidade() == 0);
    }

    @Test
    void atualizaProdutoInativo() {
        Produto produto = produtoComEstoque(false, Map.of());

        EstoqueProdutoDTO resposta = produtoAdminService.atualizarEstoque(produto.getId(),
                List.of(new EstoqueDTO("G", 2)));

        assertThat(resposta.estoque()).containsExactly(new EstoqueDTO("G", 2));
    }

    @Test
    void tamanhoRepetidoFalha() {
        Produto produto = produtoComEstoque(true, Map.of(Tamanho.P, 3));

        assertThatThrownBy(() -> produtoAdminService.atualizarEstoque(produto.getId(),
                List.of(new EstoqueDTO("P", 1), new EstoqueDTO("P", 2))))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Tamanho P repetido no estoque");
    }

    @Test
    void produtoInexistenteFalha() {
        assertThatThrownBy(() -> produtoAdminService.atualizarEstoque(-1L, List.of(new EstoqueDTO("P", 1))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // Grava e limpa o contexto: a atualização chega em outra requisição, com o produto vindo do banco
    private Produto produtoComEstoque(boolean ativo, Map<Tamanho, Integer> quantidades) {
        Produto produto = Produto.builder()
                .nome("Produto estoque")
                .preco(new BigDecimal("99.90"))
                .categoria(categoria)
                .ativo(ativo)
                .build();
        quantidades.forEach((tamanho, quantidade) -> produto.getEstoque().add(ProdutoEstoque.builder()
                .produto(produto)
                .tamanho(tamanho)
                .quantidade(quantidade)
                .build()));
        produtoRepository.save(produto);
        entityManager.flush();
        entityManager.clear();
        return produto;
    }

    private ProdutoResumoDTO resumoNaVitrine(Produto produto) {
        return produtoService.listar(categoria.getId(), null, 0, 12).conteudo().stream()
                .filter(p -> p.id().equals(produto.getId()))
                .findFirst()
                .orElseThrow();
    }
}
