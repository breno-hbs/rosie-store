package com.rosie.backend.service;

import com.rosie.backend.dto.ProdutoImagemDTO;
import com.rosie.backend.exception.ArquivoInvalidoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.ProdutoImagem;
import com.rosie.backend.repository.CategoriaRepository;
import com.rosie.backend.repository.ProdutoImagemRepository;
import com.rosie.backend.repository.ProdutoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Fotos do produto (RF15) contra o MySQL local, com o Cloudinary trocado por um falso;
// @Transactional desfaz tudo no fim
@SpringBootTest
@Transactional
@Import(ArmazenamentoImagensFalso.Config.class)
class ProdutoImagemServiceTests {

    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};
    static final byte[] WEBP = "RIFF$\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] GIF = "GIF89a\u0001\0\u0001\0".getBytes(StandardCharsets.ISO_8859_1);

    @Autowired
    private ProdutoImagemService produtoImagemService;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private ProdutoAdminService produtoAdminService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ProdutoImagemRepository produtoImagemRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ArmazenamentoImagensFalso armazenamento;

    @Autowired
    private EntityManager entityManager;

    private Categoria categoria;

    @BeforeEach
    void setUp() {
        armazenamento.limpar();
        categoria = categoriaRepository.save(Categoria.builder().nome("Vestido fotos " + System.nanoTime()).build());
    }

    @Test
    void fotoEnviadaEntraNoFimDaGaleriaComUrlEPublicId() {
        Produto produto = produtoComImagens("https://placehold.co/1000x1250?text=antiga");

        ProdutoImagemDTO nova = produtoImagemService.enviar(produto.getId(), arquivo("foto.png", PNG));

        assertThat(nova.id()).isNotNull();
        assertThat(nova.ordem()).isEqualTo(1);
        assertThat(nova.url()).isEqualTo("https://res.cloudinary.test/rosie/produtos/teste-1.jpg");
        assertThat(armazenamento.enviados).hasSize(1);
        assertThat(armazenamento.enviados.get(0)).isEqualTo(PNG);

        entityManager.clear();
        ProdutoImagem salva = produtoImagemRepository.findById(nova.id()).orElseThrow();
        assertThat(salva.getPublicId()).isEqualTo("rosie/produtos/teste-1");
        // a imagem antiga continua como principal, sem public_id
        assertThat(produtoService.buscarPorId(produto.getId()).imagens()).containsExactly(
                "https://placehold.co/1000x1250?text=antiga", nova.url());
    }

    @Test
    void aceitaJpgPngEWebpPeloConteudoMesmoComNomeEContentTypeErrados() {
        Produto produto = produtoComImagens();

        produtoImagemService.enviar(produto.getId(), new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", JPG));
        produtoImagemService.enviar(produto.getId(), new MockMultipartFile("arquivo", "foto", "application/octet-stream", PNG));
        ProdutoImagemDTO terceira = produtoImagemService.enviar(produto.getId(),
                new MockMultipartFile("arquivo", "foto.webp", "image/webp", WEBP));

        assertThat(armazenamento.enviados).hasSize(3);
        assertThat(terceira.ordem()).isEqualTo(2);
    }

    @Test
    void formatoForaDeJpgPngWebpFalhaSemEnviar() {
        Produto produto = produtoComImagens();

        assertThatThrownBy(() -> produtoImagemService.enviar(produto.getId(), arquivo("foto.gif", GIF)))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessage("Formato não aceito: envie a foto em JPG, PNG ou WEBP")
                .extracting("codigo").isEqualTo("FORMATO_NAO_SUPORTADO");
        // extensão de imagem não basta: o conteúdo é que manda
        assertThatThrownBy(() -> produtoImagemService.enviar(produto.getId(),
                new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "não sou foto".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ArquivoInvalidoException.class);
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void arquivoAcimaDe5MbFalhaSemEnviar() {
        Produto produto = produtoComImagens();
        byte[] grande = Arrays.copyOf(JPG, 5 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> produtoImagemService.enviar(produto.getId(), arquivo("grande.jpg", grande)))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessage("A foto passa de 5 MB: diminua o arquivo e tente de novo")
                .extracting("codigo").isEqualTo("ARQUIVO_MUITO_GRANDE");
        assertThat(armazenamento.enviados).isEmpty();

        // exatamente 5 MB passa
        produtoImagemService.enviar(produto.getId(), arquivo("limite.jpg", Arrays.copyOf(JPG, 5 * 1024 * 1024)));
        assertThat(armazenamento.enviados).hasSize(1);
    }

    @Test
    void arquivoVazioFalha() {
        Produto produto = produtoComImagens();

        assertThatThrownBy(() -> produtoImagemService.enviar(produto.getId(), arquivo("vazio.jpg", new byte[0])))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Envie a foto no campo arquivo");
    }

    @Test
    void produtoInexistenteFalhaSemEnviar() {
        assertThatThrownBy(() -> produtoImagemService.enviar(-1L, arquivo("foto.jpg", JPG)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void removerTiraDoProdutoRenumeraEApagaDoArmazenamento() {
        Produto produto = produtoComImagens();
        ProdutoImagemDTO primeira = produtoImagemService.enviar(produto.getId(), arquivo("1.jpg", JPG));
        ProdutoImagemDTO segunda = produtoImagemService.enviar(produto.getId(), arquivo("2.jpg", JPG));
        limparContexto();

        produtoImagemService.remover(produto.getId(), primeira.id());

        assertThat(armazenamento.removidos).containsExactly("rosie/produtos/teste-1");
        entityManager.clear();
        // a segunda vira a principal
        assertThat(produtoAdminService.buscarPorId(produto.getId()).imagens())
                .containsExactly(new ProdutoImagemDTO(segunda.id(), segunda.url(), 0));
    }

    @Test
    void removerImagemAntigaSemPublicIdNaoChamaOArmazenamento() {
        Produto produto = produtoComImagens("https://placehold.co/1000x1250?text=antiga");
        Long antiga = produto.getImagens().get(0).getId();

        produtoImagemService.remover(produto.getId(), antiga);

        assertThat(armazenamento.removidos).isEmpty();
        entityManager.clear();
        assertThat(produtoImagemRepository.findById(antiga)).isEmpty();
    }

    @Test
    void falhaNoArmazenamentoNaoImpedeARemocao() {
        Produto produto = produtoComImagens();
        ProdutoImagemDTO foto = produtoImagemService.enviar(produto.getId(), arquivo("1.jpg", JPG));
        limparContexto();
        armazenamento.falharNaRemocao = true;

        produtoImagemService.remover(produto.getId(), foto.id());

        entityManager.clear();
        assertThat(produtoImagemRepository.findById(foto.id())).isEmpty();
    }

    @Test
    void removerFotoDeOutroProdutoOuInexistenteFalha() {
        Produto produto = produtoComImagens();
        Produto outro = produtoComImagens("https://placehold.co/1000x1250?text=outro");
        Long fotoDoOutro = outro.getImagens().get(0).getId();

        assertThatThrownBy(() -> produtoImagemService.remover(produto.getId(), fotoDoOutro))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> produtoImagemService.remover(produto.getId(), -1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> produtoImagemService.remover(-1L, fotoDoOutro))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void reordenarDefineAPrincipalNaVitrine() {
        Produto produto = produtoComImagens("https://placehold.co/a", "https://placehold.co/b", "https://placehold.co/c");
        List<Long> ids = produto.getImagens().stream().map(ProdutoImagem::getId).toList();

        List<ProdutoImagemDTO> resposta = produtoImagemService.reordenar(produto.getId(),
                List.of(ids.get(2), ids.get(0), ids.get(1)));

        assertThat(resposta).extracting(ProdutoImagemDTO::url)
                .containsExactly("https://placehold.co/c", "https://placehold.co/a", "https://placehold.co/b");
        assertThat(resposta).extracting(ProdutoImagemDTO::ordem).containsExactly(0, 1, 2);

        entityManager.clear();
        assertThat(produtoService.buscarPorId(produto.getId()).imagens())
                .containsExactly("https://placehold.co/c", "https://placehold.co/a", "https://placehold.co/b");
        assertThat(produtoService.listar(categoria.getId(), 0, 12).conteudo().get(0).imagemPrincipal())
                .isEqualTo("https://placehold.co/c");
    }

    @Test
    void reordenarSemTodasAsFotosOuComRepeticaoFalha() {
        Produto produto = produtoComImagens("https://placehold.co/a", "https://placehold.co/b");
        Produto outro = produtoComImagens("https://placehold.co/outro");
        List<Long> ids = produto.getImagens().stream().map(ProdutoImagem::getId).toList();
        Long fotoDoOutro = outro.getImagens().get(0).getId();

        for (List<Long> invalida : List.of(
                List.of(ids.get(0)),
                List.of(ids.get(0), ids.get(0)),
                List.of(ids.get(0), ids.get(1), ids.get(1)),
                List.of(ids.get(0), fotoDoOutro))) {
            assertThatThrownBy(() -> produtoImagemService.reordenar(produto.getId(), invalida))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessage("Envie todas as fotos do produto, cada uma uma vez");
        }
    }

    private MockMultipartFile arquivo(String nome, byte[] conteudo) {
        return new MockMultipartFile("arquivo", nome, "image/jpeg", conteudo);
    }

    // Grava e limpa o contexto: as operações chegam em outra requisição, com o produto vindo do banco
    private Produto produtoComImagens(String... urls) {
        Produto produto = Produto.builder()
                .nome("Produto fotos")
                .preco(new BigDecimal("99.90"))
                .categoria(categoria)
                .build();
        for (int i = 0; i < urls.length; i++) {
            produto.getImagens().add(ProdutoImagem.builder().produto(produto).url(urls[i]).ordem(i).build());
        }
        produtoRepository.save(produto);
        limparContexto();
        return produto;
    }

    private void limparContexto() {
        entityManager.flush();
        entityManager.clear();
    }
}
