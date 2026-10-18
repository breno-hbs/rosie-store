package com.rosie.backend.service;

import com.rosie.backend.dto.FreteResponseDTO;
import com.rosie.backend.exception.ConflitoException;
import com.rosie.backend.exception.FreteFalhouException;
import com.rosie.backend.model.CarrinhoItem;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoEstoque;
import com.rosie.backend.model.Role;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.CarrinhoItemRepository;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoRepository;
import com.rosie.backend.repository.UsuarioRepository;
import com.rosie.backend.service.CalculadoraFrete.OpcaoFrete;
import com.rosie.backend.service.CalculadoraFrete.PecaFrete;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Frete do carrinho com o Melhor Envio falso; usa o MySQL local e desfaz tudo ao fim de cada teste
@SpringBootTest
@Transactional
@Import(CalculadoraFreteFalsa.Config.class)
class FreteServiceTests {

    @Autowired
    private FreteService freteService;

    @Autowired
    private CalculadoraFreteFalsa calculadora;

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

    private Usuario cliente;
    private Produto produto;

    @BeforeEach
    void setUp() {
        calculadora.limpar();
        cliente = usuarioRepository.save(Usuario.builder()
                .nome("Cliente Frete")
                .email("cliente-frete@rosie.test")
                .senhaHash("hash-irrelevante")
                .role(Role.CLIENTE)
                .build());

        Categoria categoria = categoriaRepository.save(Categoria.builder().nome("Frete " + System.nanoTime()).build());
        produto = Produto.builder()
                .nome("Vestido Aurora")
                .preco(new BigDecimal("159.90"))
                .categoria(categoria)
                .build();
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.P).quantidade(3).build());
        produto.getEstoque().add(ProdutoEstoque.builder().produto(produto).tamanho(Tamanho.M).quantidade(0).build());
        produtoRepository.save(produto);
    }

    @Test
    void cotaAsPecasDisponiveisComOPacotePadraoEOrdenaPeloPreco() {
        adicionar(Tamanho.P, 2);
        adicionar(Tamanho.M, 1); // esgotado: não entra na cotação, como não entra no subtotal
        calculadora.opcoes = List.of(
                new OpcaoFrete(2, "Correios", "SEDEX", new BigDecimal("38.50"), 3),
                new OpcaoFrete(1, "Correios", "PAC", new BigDecimal("22.90"), 7));

        FreteResponseDTO resposta = freteService.calcular(cliente.getId(), "03925-050");

        assertThat(resposta.cep()).isEqualTo("03925050");
        assertThat(resposta.opcoes()).extracting("servico").containsExactly("PAC", "SEDEX");
        assertThat(resposta.opcoes().get(0).id()).isEqualTo(1);
        assertThat(resposta.opcoes().get(0).transportadora()).isEqualTo("Correios");
        assertThat(resposta.opcoes().get(0).preco()).isEqualByComparingTo("22.90");
        assertThat(resposta.opcoes().get(0).prazoDias()).isEqualTo(7);

        assertThat(calculadora.chamadas).hasSize(1);
        CalculadoraFreteFalsa.Chamada chamada = calculadora.chamadas.get(0);
        assertThat(chamada.cepDestino()).isEqualTo("03925050");
        assertThat(chamada.pecas()).containsExactly(new PecaFrete(
                "produto-" + produto.getId() + "-P", 2, new BigDecimal("159.90"),
                new BigDecimal("0.3"), 20, 5, 30));
    }

    @Test
    void semOpcoesDevolveListaVazia() {
        adicionar(Tamanho.P, 1);

        FreteResponseDTO resposta = freteService.calcular(cliente.getId(), "99999999");

        assertThat(resposta.opcoes()).isEmpty();
    }

    @Test
    void carrinhoVazioNaoChamaOMelhorEnvio() {
        assertThatThrownBy(() -> freteService.calcular(cliente.getId(), "03925050"))
                .isInstanceOf(ConflitoException.class)
                .hasFieldOrPropertyWithValue("codigo", "CARRINHO_VAZIO");
        assertThat(calculadora.chamadas).isEmpty();
    }

    @Test
    void carrinhoSoComPecasIndisponiveisContaComoVazio() {
        adicionar(Tamanho.M, 1);

        assertThatThrownBy(() -> freteService.calcular(cliente.getId(), "03925050"))
                .isInstanceOf(ConflitoException.class)
                .hasFieldOrPropertyWithValue("codigo", "CARRINHO_VAZIO");
    }

    @Test
    void falhaDoMelhorEnvioSobeSemMexerNoCarrinho() {
        adicionar(Tamanho.P, 1);
        calculadora.falha = new FreteFalhouException("fora do ar (teste)");

        assertThatThrownBy(() -> freteService.calcular(cliente.getId(), "03925050"))
                .isInstanceOf(FreteFalhouException.class);
        assertThat(carrinhoItemRepository.findByUsuarioIdOrderByIdAsc(cliente.getId())).hasSize(1);
    }

    private void adicionar(Tamanho tamanho, int quantidade) {
        carrinhoItemRepository.save(CarrinhoItem.builder()
                .usuario(cliente)
                .produto(produto)
                .tamanho(tamanho)
                .quantidade(quantidade)
                .build());
        entityManager.flush();
        entityManager.clear();
    }
}
