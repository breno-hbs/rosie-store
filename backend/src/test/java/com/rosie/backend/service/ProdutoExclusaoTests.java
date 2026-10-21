package com.rosie.backend.service;

import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Fotos da exclusão definitiva (#87) no armazenamento falso, sem a transação do teste: elas só saem depois
// do commit, então a exclusão precisa ser confirmada no MySQL de verdade. Apaga o que sobrar no fim.
@SpringBootTest
@Import(ArmazenamentoImagensFalso.Config.class)
class ProdutoExclusaoTests {

    @Autowired
    private ProdutoAdminService produtoAdminService;

    @Autowired
    private ArmazenamentoImagensFalso armazenamento;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TransactionTemplate transacao;

    private Categoria categoria;
    private Produto produto;

    @BeforeEach
    void setUp() {
        armazenamento.limpar();
        categoria = categoriaRepository.save(Categoria.builder().nome("Exclusão " + System.nanoTime()).build());

        Produto novo = Produto.builder()
                .nome("Cadastro errado").preco(new BigDecimal("189.00")).categoria(categoria).build();
        novo.getImagens().add(ProdutoImagem.builder().produto(novo).url(ArmazenamentoImagensFalso.ORIGINAL + "a.jpg")
                .publicId("rosie/produtos/errado-a").ordem(0).build());
        novo.getImagens().add(ProdutoImagem.builder().produto(novo).url(ArmazenamentoImagensFalso.ORIGINAL + "b.jpg")
                .publicId("rosie/produtos/errado-b").ordem(1).build());
        // Placeholder antigo, sem public_id: não está no Cloudinary
        novo.getImagens().add(ProdutoImagem.builder().produto(novo).url("https://exemplo.test/antiga.jpg")
                .ordem(2).build());
        produto = produtoRepository.save(novo);
    }

    @AfterEach
    void limparBanco() {
        transacao.executeWithoutResult(status -> {
            produtoRepository.findById(produto.getId()).ifPresent(produtoRepository::delete);
            categoriaRepository.deleteById(categoria.getId());
        });
    }

    @Test
    void excluirApagaAsFotosDoArmazenamentoDepoisDoCommit() {
        produtoAdminService.excluirDefinitivamente(produto.getId());

        assertThat(produtoRepository.findById(produto.getId())).isEmpty();
        assertThat(armazenamento.removidos).containsExactlyInAnyOrder("rosie/produtos/errado-a", "rosie/produtos/errado-b");
    }

    @Test
    void falhaNoArmazenamentoNaoDesfazAExclusao() {
        armazenamento.falharNaRemocao = true;

        produtoAdminService.excluirDefinitivamente(produto.getId());

        assertThat(produtoRepository.findById(produto.getId())).isEmpty();
        assertThat(armazenamento.removidos).isEmpty();
    }

    @Test
    void exclusaoDesfeitaNaoApagaAsFotos() {
        transacao.executeWithoutResult(status -> {
            produtoAdminService.excluirDefinitivamente(produto.getId());
            status.setRollbackOnly();
        });

        assertThat(produtoRepository.findById(produto.getId())).isPresent();
        assertThat(armazenamento.removidos).isEmpty();
    }
}
