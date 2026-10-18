package com.rosie.backend.service;

import com.rosie.backend.dto.CarrinhoDTO;
import com.rosie.backend.dto.CarrinhoItemDTO;
import com.rosie.backend.dto.CarrinhoItemRequestDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
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
class CarrinhoServiceTests {

    @Autowired
    private CarrinhoService carrinhoService;

    @Autowired
    private CarrinhoItemRepository carrinhoItemRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EntityManager entityManager;

    private Long clienteId;
    private Long outroClienteId;
    private Produto produto;

    @BeforeEach
    void setUp() {
        clienteId = salvarCliente("cliente-carrinho@rosie.test").getId();
        outroClienteId = salvarCliente("outro-carrinho@rosie.test").getId();

        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Carrinho " + System.nanoTime()).build());
        produto = Produto.builder()
                .nome("Conjunto Serpente")
                .preco(new BigDecimal("189.00"))
                .categoria(categoria)
                .build();
        produto.getEstoque().add(linha(Tamanho.P, 3));
        produto.getEstoque().add(linha(Tamanho.M, 5));
        produto.getEstoque().add(linha(Tamanho.GG, 0));
        produto.getImagens().add(ProdutoImagem.builder().produto(produto).url("https://placehold.co/b").ordem(1).build());
        produto.getImagens().add(ProdutoImagem.builder().produto(produto).url("https://placehold.co/a").ordem(0).build());
        produtoRepository.save(produto);
        sincronizar();
    }

    @Test
    void carrinhoNovoVemVazio() {
        CarrinhoDTO carrinho = carrinhoService.buscar(clienteId);

        assertThat(carrinho.itens()).isEmpty();
        assertThat(carrinho.quantidadeItens()).isZero();
        assertThat(carrinho.subtotal()).isEqualByComparingTo("0");
        assertThat(carrinho.temIndisponiveis()).isFalse();
    }

    @Test
    void adicionarDevolveOCarrinhoComPrecoFotoESubtotal() {
        CarrinhoDTO carrinho = carrinhoService.adicionar(clienteId, item(Tamanho.M, 2));

        assertThat(carrinho.itens()).hasSize(1);
        CarrinhoItemDTO item = carrinho.itens().get(0);
        assertThat(item.produto().id()).isEqualTo(produto.getId());
        assertThat(item.produto().nome()).isEqualTo("Conjunto Serpente");
        assertThat(item.produto().preco()).isEqualByComparingTo("189.00");
        assertThat(item.produto().imagemPrincipal()).isEqualTo("https://placehold.co/a");
        assertThat(item.tamanho()).isEqualTo("M");
        assertThat(item.quantidade()).isEqualTo(2);
        assertThat(item.subtotal()).isEqualByComparingTo("378.00");
        assertThat(item.estoqueDisponivel()).isEqualTo(5);
        assertThat(item.disponivel()).isTrue();
        assertThat(item.motivoIndisponivel()).isNull();
        assertThat(carrinho.quantidadeItens()).isEqualTo(2);
        assertThat(carrinho.subtotal()).isEqualByComparingTo("378.00");
    }

    @Test
    void mesmoProdutoETamanhoSomamNoMesmoItem() {
        carrinhoService.adicionar(clienteId, item(Tamanho.M, 1));
        sincronizar();
        CarrinhoDTO carrinho = carrinhoService.adicionar(clienteId, item(Tamanho.M, 2));

        assertThat(carrinho.itens()).hasSize(1);
        assertThat(carrinho.itens().get(0).quantidade()).isEqualTo(3);
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(clienteId)).hasSize(1);
    }

    @Test
    void tamanhosDiferentesViramItensSeparadosNaOrdemEmQueEntraram() {
        carrinhoService.adicionar(clienteId, item(Tamanho.M, 1));
        CarrinhoDTO carrinho = carrinhoService.adicionar(clienteId, item(Tamanho.P, 1));

        assertThat(carrinho.itens()).extracting(CarrinhoItemDTO::tamanho).containsExactly("M", "P");
        assertThat(carrinho.quantidadeItens()).isEqualTo(2);
        assertThat(carrinho.subtotal()).isEqualByComparingTo("378.00");
    }

    @Test
    void naoDeixaPassarDoEstoqueDoTamanho() {
        assertThatThrownBy(() -> carrinhoService.adicionar(clienteId, item(Tamanho.P, 4)))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Só temos 3 no tamanho P")
                .extracting("codigo").isEqualTo("ESTOQUE_INSUFICIENTE");
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(clienteId)).isEmpty();
    }

    @Test
    void somaComOQueJaEstaNaSacolaTambemRespeitaOEstoque() {
        carrinhoService.adicionar(clienteId, item(Tamanho.P, 2));
        sincronizar();

        assertThatThrownBy(() -> carrinhoService.adicionar(clienteId, item(Tamanho.P, 2)))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Só temos 3 no tamanho P, e você já tem 2 na sacola");
        sincronizar();
        assertThat(carrinhoService.buscar(clienteId).itens().get(0).quantidade()).isEqualTo(2);
    }

    @Test
    void tamanhoEsgotadoOuSemEstoqueCadastradoDa409() {
        assertThatThrownBy(() -> carrinhoService.adicionar(clienteId, item(Tamanho.GG, 1)))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("O tamanho GG está esgotado")
                .extracting("codigo").isEqualTo("ESTOQUE_INSUFICIENTE");
        // G não tem linha de estoque: conta como esgotado
        assertThatThrownBy(() -> carrinhoService.adicionar(clienteId, item(Tamanho.G, 1)))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("O tamanho G está esgotado");
    }

    @Test
    void produtoInativoDa409EInexistenteDa400() {
        desativarProduto();

        assertThatThrownBy(() -> carrinhoService.adicionar(clienteId, item(Tamanho.M, 1)))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("PRODUTO_INDISPONIVEL");
        assertThatThrownBy(() -> carrinhoService.adicionar(clienteId,
                new CarrinhoItemRequestDTO(Long.MAX_VALUE, "M", 1)))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Produto não encontrado");
    }

    @Test
    void atualizarQuantidadeTrocaOValorERespeitaOEstoque() {
        Long itemId = carrinhoService.adicionar(clienteId, item(Tamanho.M, 1)).itens().get(0).id();
        sincronizar();

        CarrinhoDTO carrinho = carrinhoService.atualizarQuantidade(clienteId, itemId, 5);
        assertThat(carrinho.itens().get(0).quantidade()).isEqualTo(5);
        assertThat(carrinho.subtotal()).isEqualByComparingTo("945.00");

        sincronizar();
        assertThatThrownBy(() -> carrinhoService.atualizarQuantidade(clienteId, itemId, 6))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Só temos 5 no tamanho M");
    }

    @Test
    void removerTiraOItemEDevolveOCarrinho() {
        carrinhoService.adicionar(clienteId, item(Tamanho.P, 1));
        Long itemId = carrinhoService.adicionar(clienteId, item(Tamanho.M, 1)).itens().get(1).id();
        sincronizar();

        CarrinhoDTO carrinho = carrinhoService.remover(clienteId, itemId);

        assertThat(carrinho.itens()).extracting(CarrinhoItemDTO::tamanho).containsExactly("P");
        assertThat(carrinhoItemRepository.findById(itemId)).isEmpty();
    }

    @Test
    void itemDeOutroClienteNaoEEncontrado() {
        Long itemId = carrinhoService.adicionar(outroClienteId, item(Tamanho.M, 1)).itens().get(0).id();
        sincronizar();

        assertThatThrownBy(() -> carrinhoService.atualizarQuantidade(clienteId, itemId, 2))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> carrinhoService.remover(clienteId, itemId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(carrinhoService.buscar(clienteId).itens()).isEmpty();
        assertThat(carrinhoService.buscar(outroClienteId).itens()).hasSize(1);
    }

    @Test
    void itemQueFicouSemEstoqueSuficienteVemMarcadoESaiDoSubtotal() {
        carrinhoService.adicionar(clienteId, item(Tamanho.P, 1));
        carrinhoService.adicionar(clienteId, item(Tamanho.M, 4));
        mudarEstoque(Tamanho.M, 2);

        CarrinhoDTO carrinho = carrinhoService.buscar(clienteId);

        CarrinhoItemDTO itemM = carrinho.itens().get(1);
        assertThat(itemM.disponivel()).isFalse();
        assertThat(itemM.motivoIndisponivel()).isEqualTo("ESTOQUE_INSUFICIENTE");
        assertThat(itemM.estoqueDisponivel()).isEqualTo(2);
        assertThat(itemM.quantidade()).isEqualTo(4);
        assertThat(carrinho.temIndisponiveis()).isTrue();
        assertThat(carrinho.quantidadeItens()).isEqualTo(5);
        // só o P (disponível) entra no subtotal
        assertThat(carrinho.subtotal()).isEqualByComparingTo("189.00");
    }

    @Test
    void tamanhoQueEsgotouEProdutoDesativadoFicamNoCarrinhoMarcados() {
        carrinhoService.adicionar(clienteId, item(Tamanho.P, 1));
        mudarEstoque(Tamanho.P, 0);
        assertThat(carrinhoService.buscar(clienteId).itens().get(0).motivoIndisponivel())
                .isEqualTo("TAMANHO_ESGOTADO");

        desativarProduto();
        CarrinhoDTO carrinho = carrinhoService.buscar(clienteId);
        assertThat(carrinho.itens()).hasSize(1);
        assertThat(carrinho.itens().get(0).motivoIndisponivel()).isEqualTo("PRODUTO_INATIVO");
        assertThat(carrinho.subtotal()).isEqualByComparingTo("0");
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(clienteId)).hasSize(1);
    }

    @Test
    void diminuirItemQueFicouAcimaDoEstoqueVoltaASerDisponivel() {
        Long itemId = carrinhoService.adicionar(clienteId, item(Tamanho.M, 4)).itens().get(0).id();
        mudarEstoque(Tamanho.M, 2);

        CarrinhoDTO carrinho = carrinhoService.atualizarQuantidade(clienteId, itemId, 2);

        assertThat(carrinho.itens().get(0).disponivel()).isTrue();
        assertThat(carrinho.temIndisponiveis()).isFalse();
    }

    @Test
    void naoMudaQuantidadeDeProdutoDesativado() {
        Long itemId = carrinhoService.adicionar(clienteId, item(Tamanho.M, 2)).itens().get(0).id();
        desativarProduto();

        assertThatThrownBy(() -> carrinhoService.atualizarQuantidade(clienteId, itemId, 1))
                .isInstanceOf(ConflitoException.class)
                .extracting("codigo").isEqualTo("PRODUTO_INDISPONIVEL");
        // mas dá para remover
        assertThat(carrinhoService.remover(clienteId, itemId).itens()).isEmpty();
    }

    @Test
    void carrinhoNaoBaixaOEstoque() {
        carrinhoService.adicionar(clienteId, item(Tamanho.M, 5));
        sincronizar();

        Produto recarregado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertThat(CarrinhoItemDTO.estoqueDoTamanho(recarregado, Tamanho.M)).isEqualTo(5);
    }

    private CarrinhoItemRequestDTO item(Tamanho tamanho, int quantidade) {
        return new CarrinhoItemRequestDTO(produto.getId(), tamanho.name(), quantidade);
    }

    private void mudarEstoque(Tamanho tamanho, int quantidade) {
        sincronizar();
        Produto recarregado = produtoRepository.findById(produto.getId()).orElseThrow();
        recarregado.getEstoque().stream()
                .filter(estoque -> estoque.getTamanho() == tamanho)
                .forEach(estoque -> estoque.setQuantidade(quantidade));
        sincronizar();
    }

    private void desativarProduto() {
        sincronizar();
        produtoRepository.findById(produto.getId()).orElseThrow().setAtivo(false);
        sincronizar();
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    private Usuario salvarCliente(String email) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Cliente Carrinho")
                .email(email)
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());
    }

    private ProdutoEstoque linha(Tamanho tamanho, int quantidade) {
        return ProdutoEstoque.builder().produto(produto).tamanho(tamanho).quantidade(quantidade).build();
    }
}
