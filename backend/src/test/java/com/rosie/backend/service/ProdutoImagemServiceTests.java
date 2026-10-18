package com.rosie.backend.service;

import com.rosie.backend.dto.ProdutoImagemDTO;
import com.rosie.backend.exception.ArquivoInvalidoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Categoria;
import com.rosie.backend.model.Enquadramento;
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
import static org.assertj.core.api.Assertions.tuple;

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

        ProdutoImagemDTO nova = enviar(produto.getId(), arquivo("foto.png", PNG));

        assertThat(nova.id()).isNotNull();
        assertThat(nova.ordem()).isEqualTo(1);
        // entregue já em 4:5; sem enquadramento escolhido, fica no automático
        assertThat(nova.url()).isEqualTo(ArmazenamentoImagensFalso.ENTREGA + "rosie/produtos/teste-1.jpg");
        assertThat(nova.enquadramento()).isEqualTo(Enquadramento.AUTOMATICO);
        assertThat(armazenamento.enviados).hasSize(1);
        assertThat(armazenamento.enviados.get(0)).isEqualTo(PNG);

        entityManager.clear();
        ProdutoImagem salva = produtoImagemRepository.findById(nova.id()).orElseThrow();
        assertThat(salva.getPublicId()).isEqualTo("rosie/produtos/teste-1");
        // o banco guarda a URL do original, para o corte poder mudar depois
        assertThat(salva.getUrl()).isEqualTo(ArmazenamentoImagensFalso.ORIGINAL + "rosie/produtos/teste-1.jpg");
        assertThat(salva.getEnquadramento()).isEqualTo(Enquadramento.AUTOMATICO);
        // a imagem antiga continua como principal, sem public_id e sem corte
        assertThat(produtoService.buscarPorId(produto.getId()).imagens()).containsExactly(
                "https://placehold.co/1000x1250?text=antiga", nova.url());
    }

    @Test
    void enquadramentoEscolhidoFicaGuardadoEMudaOCorteNaVitrineENoPainel() {
        Produto produto = produtoComImagens();

        ProdutoImagemDTO topo = produtoImagemService.enviar(produto.getId(), arquivo("1.jpg", JPG), "TOPO");
        // aceita minúsculas, como vem de um formulário
        ProdutoImagemDTO centro = produtoImagemService.enviar(produto.getId(), arquivo("2.jpg", JPG), "centro");
        ProdutoImagemDTO automatico = produtoImagemService.enviar(produto.getId(), arquivo("3.jpg", JPG), " ");

        assertThat(topo.enquadramento()).isEqualTo(Enquadramento.TOPO);
        assertThat(centro.enquadramento()).isEqualTo(Enquadramento.CENTRO);
        assertThat(automatico.enquadramento()).isEqualTo(Enquadramento.AUTOMATICO);

        entityManager.clear();
        String base = "https://res.cloudinary.com/rosie-teste/image/upload/c_fill,ar_4:5,";
        String resto = "/c_limit,w_1000/f_auto,q_auto/v1/rosie/produtos/teste-";
        assertThat(produtoService.buscarPorId(produto.getId()).imagens()).containsExactly(
                base + "g_north" + resto + "1.jpg",
                base + "g_center" + resto + "2.jpg",
                base + "g_auto" + resto + "3.jpg");
        assertThat(produtoService.listar(categoria.getId(), 0, 12).conteudo().get(0).imagemPrincipal())
                .isEqualTo(base + "g_north" + resto + "1.jpg");
        assertThat(produtoAdminService.buscarPorId(produto.getId()).imagens())
                .extracting(ProdutoImagemDTO::url, ProdutoImagemDTO::enquadramento)
                .containsExactly(
                        tuple(base + "g_north" + resto + "1.jpg", Enquadramento.TOPO),
                        tuple(base + "g_center" + resto + "2.jpg", Enquadramento.CENTRO),
                        tuple(base + "g_auto" + resto + "3.jpg", Enquadramento.AUTOMATICO));
    }

    @Test
    void enquadramentoInvalidoFalhaSemEnviar() {
        Produto produto = produtoComImagens();

        assertThatThrownBy(() -> produtoImagemService.enviar(produto.getId(), arquivo("1.jpg", JPG), "esquerda"))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Enquadramento inválido: use AUTOMATICO, CENTRO ou TOPO");
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void fotoEnviadaAntesDoCorteFicaNoAutomatico() {
        Produto produto = produtoComImagens();
        ProdutoImagemDTO foto = enviar(produto.getId(), arquivo("1.jpg", JPG));
        limparContexto();
        // como as fotos da #15, gravadas antes de existir a coluna enquadramento
        entityManager.createNativeQuery("update produto_imagem set enquadramento = null where id = :id")
                .setParameter("id", foto.id())
                .executeUpdate();
        entityManager.clear();

        assertThat(produtoAdminService.buscarPorId(produto.getId()).imagens())
                .containsExactly(new ProdutoImagemDTO(foto.id(), foto.url(), 0, Enquadramento.AUTOMATICO));
    }

    @Test
    void aceitaJpgPngEWebpPeloConteudoMesmoComNomeEContentTypeErrados() {
        Produto produto = produtoComImagens();

        enviar(produto.getId(), new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", JPG));
        enviar(produto.getId(), new MockMultipartFile("arquivo", "foto", "application/octet-stream", PNG));
        ProdutoImagemDTO terceira = enviar(produto.getId(),
                new MockMultipartFile("arquivo", "foto.webp", "image/webp", WEBP));

        assertThat(armazenamento.enviados).hasSize(3);
        assertThat(terceira.ordem()).isEqualTo(2);
    }

    @Test
    void formatoForaDeJpgPngWebpFalhaSemEnviar() {
        Produto produto = produtoComImagens();

        assertThatThrownBy(() -> enviar(produto.getId(), arquivo("foto.gif", GIF)))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessage("Formato não aceito: envie a foto em JPG, PNG ou WEBP")
                .extracting("codigo").isEqualTo("FORMATO_NAO_SUPORTADO");
        // extensão de imagem não basta: o conteúdo é que manda
        assertThatThrownBy(() -> enviar(produto.getId(),
                new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "não sou foto".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ArquivoInvalidoException.class);
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void arquivoAcimaDe5MbFalhaSemEnviar() {
        Produto produto = produtoComImagens();
        byte[] grande = Arrays.copyOf(JPG, 5 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> enviar(produto.getId(), arquivo("grande.jpg", grande)))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessage("A foto passa de 5 MB: diminua o arquivo e tente de novo")
                .extracting("codigo").isEqualTo("ARQUIVO_MUITO_GRANDE");
        assertThat(armazenamento.enviados).isEmpty();

        // exatamente 5 MB passa
        enviar(produto.getId(), arquivo("limite.jpg", Arrays.copyOf(JPG, 5 * 1024 * 1024)));
        assertThat(armazenamento.enviados).hasSize(1);
    }

    @Test
    void arquivoVazioFalha() {
        Produto produto = produtoComImagens();

        assertThatThrownBy(() -> enviar(produto.getId(), arquivo("vazio.jpg", new byte[0])))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Envie a foto no campo arquivo");
    }

    @Test
    void produtoInexistenteFalhaSemEnviar() {
        assertThatThrownBy(() -> enviar(-1L, arquivo("foto.jpg", JPG)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void removerTiraDoProdutoRenumeraEApagaDoArmazenamento() {
        Produto produto = produtoComImagens();
        ProdutoImagemDTO primeira = enviar(produto.getId(), arquivo("1.jpg", JPG));
        ProdutoImagemDTO segunda = enviar(produto.getId(), arquivo("2.jpg", JPG));
        limparContexto();

        produtoImagemService.remover(produto.getId(), primeira.id());

        assertThat(armazenamento.removidos).containsExactly("rosie/produtos/teste-1");
        entityManager.clear();
        // a segunda vira a principal
        assertThat(produtoAdminService.buscarPorId(produto.getId()).imagens())
                .containsExactly(new ProdutoImagemDTO(segunda.id(), segunda.url(), 0, Enquadramento.AUTOMATICO));
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
        ProdutoImagemDTO foto = enviar(produto.getId(), arquivo("1.jpg", JPG));
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

    // Sem enquadramento, como o painel antigo e quem chama a API sem o campo
    private ProdutoImagemDTO enviar(Long produtoId, MockMultipartFile arquivo) {
        return produtoImagemService.enviar(produtoId, arquivo, null);
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
