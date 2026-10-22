package com.rosie.backend.service;

import com.rosie.backend.dto.BannerAdminDTO;
import com.rosie.backend.dto.BannerDTO;
import com.rosie.backend.dto.BannerRequestDTO;
import com.rosie.backend.exception.ArquivoInvalidoException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.exception.RecursoNaoEncontradoException;
import com.rosie.backend.model.Banner;
import com.rosie.backend.model.SituacaoBanner;
import com.rosie.backend.repository.BannerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// Banners da Home (#89) contra o MySQL, com o Cloudinary falso e o relógio parado em 19/10/2026, 12h em
// São Paulo. Cada teste começa sem nenhum banner e é desfeito no fim (@Transactional): por isso a remoção
// das imagens depois do commit fica em BannerImagensTests.
@SpringBootTest
@Transactional
@Import({ArmazenamentoImagensFalso.Config.class, BannerServiceTests.RelogioParado.class})
class BannerServiceTests {

    static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 19, 12, 0);
    static final byte[] JPG = ProdutoImagemServiceTests.JPG;
    static final byte[] GIF = ProdutoImagemServiceTests.GIF;

    @TestConfiguration
    static class RelogioParado {

        @Bean
        @Primary
        Clock relogioParado() {
            return Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), PrazoTroca.FUSO_LOJA);
        }
    }

    @Autowired
    private BannerService bannerService;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private ArmazenamentoImagensFalso armazenamento;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        armazenamento.limpar();
        bannerRepository.deleteAllInBatch();
    }

    @Test
    void criaComAsImagensNaPastaDosBannersEEntraNoFim() {
        BannerAdminDTO primeiro = bannerService.criar(dados("Primeiro"), imagem("d.jpg"), null);
        BannerAdminDTO segundo = bannerService.criar(dados("Segundo"), imagem("d.jpg"), imagem("c.jpg"));

        assertThat(primeiro.ordem()).isZero();
        assertThat(segundo.ordem()).isEqualTo(1);
        assertThat(segundo.situacao()).isEqualTo(SituacaoBanner.NO_AR);
        assertThat(segundo.imagemDesktop().url()).isEqualTo(ArmazenamentoImagensFalso.ORIGINAL
                .replace("/v1/", "/c_fill,ar_3:1,g_auto/c_limit,w_1280/f_auto,q_auto/v1/") + "rosie/banners/teste-2.jpg");
        assertThat(segundo.imagemCelular().url()).contains("c_fill,ar_4:5,g_auto/").endsWith("rosie/banners/teste-3.jpg");
        assertThat(segundo.previaCelular()).contains("/c_limit,w_1600/f_auto,q_auto/").endsWith("teste-3.jpg");

        Banner salvo = bannerRepository.findById(segundo.id()).orElseThrow();
        assertThat(salvo.getImagemDesktopPublicId()).isEqualTo("rosie/banners/teste-2");
        assertThat(salvo.getImagemCelularPublicId()).isEqualTo("rosie/banners/teste-3");
        assertThat(salvo.getImagemDesktopUrl()).isEqualTo(ArmazenamentoImagensFalso.ORIGINAL + "rosie/banners/teste-2.jpg");
    }

    @Test
    void semImagemDeCelularUsaADeDesktopComCorteCentral() {
        BannerAdminDTO banner = bannerService.criar(dados("Verão"), imagem("d.jpg"), null);

        assertThat(banner.imagemCelular().url())
                .contains("c_fill,ar_4:5,g_center/c_limit,w_750/")
                .endsWith("rosie/banners/teste-1.jpg");
        assertThat(banner.previaCelular()).isNull();
    }

    // Nos testes a loja é a do application.properties (http://localhost:5173)
    @Test
    void textosVaziosViramNulosELinkDaLojaViraCaminho() {
        BannerRequestDTO dados = new BannerRequestDTO("  ", " Frete grátis ", "Ver", "http://localhost:5173/busca?q=linho",
                "  Modelo de vestido de linho  ", null, null, null, null);

        BannerAdminDTO banner = bannerService.criar(dados, imagem("d.jpg"), null);

        assertThat(banner.titulo()).isNull();
        assertThat(banner.texto()).isEqualTo("Frete grátis");
        assertThat(banner.link()).isEqualTo("/busca?q=linho");
        assertThat(banner.textoAlternativo()).isEqualTo("Modelo de vestido de linho");
        assertThat(banner.ativo()).isTrue();
    }

    @Test
    void homeSoRecebeAtivosDentroDoPeriodoNaOrdem() {
        Banner terceiro = salvar("Terceiro", 2, true, null, null);
        Banner primeiro = salvar("Primeiro", 0, true, AGORA.minusDays(1), AGORA.plusDays(1));
        salvar("Desativado", 1, false, null, null);
        salvar("Agendado", 3, true, AGORA.plusMinutes(1), null);
        salvar("Encerrado", 4, true, null, AGORA);
        Banner comecaAgora = salvar("Começa agora", 5, true, AGORA, AGORA.plusSeconds(1));

        List<BannerDTO> noAr = bannerService.listarNoAr();

        assertThat(noAr).extracting(BannerDTO::id)
                .containsExactly(primeiro.getId(), terceiro.getId(), comecaAgora.getId());
    }

    @Test
    void painelVeTodosComASituacao() {
        salvar("No ar", 0, true, null, AGORA.plusHours(1));
        salvar("Desativado", 1, false, null, null);
        salvar("Agendado", 2, true, AGORA.plusDays(3), null);
        salvar("Encerrado", 3, true, AGORA.minusDays(3), AGORA.minusDays(1));

        assertThat(bannerService.listarAdmin()).extracting(BannerAdminDTO::titulo, BannerAdminDTO::situacao)
                .containsExactly(
                        tuple("No ar", SituacaoBanner.NO_AR),
                        tuple("Desativado", SituacaoBanner.DESATIVADO),
                        tuple("Agendado", SituacaoBanner.AGENDADO),
                        tuple("Encerrado", SituacaoBanner.ENCERRADO));
    }

    @Test
    void linkParaForaDaLojaERecusadoSemEnviarImagem() {
        for (String link : List.of("https://golpe.com", "javascript:alert(1)", "//golpe.com")) {
            BannerRequestDTO dados = new BannerRequestDTO(null, null, null, link, "Vestido", null, null, null, null);

            assertThatThrownBy(() -> bannerService.criar(dados, imagem("d.jpg"), null))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessage(LinkBanner.MENSAGEM);
        }
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void textoDoBotaoPedeLink() {
        BannerRequestDTO dados = new BannerRequestDTO(null, null, "Comprar", null, "Vestido", null, null, null, null);

        assertThatThrownBy(() -> bannerService.criar(dados, imagem("d.jpg"), null))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Informe o link do botão, ou deixe o texto do botão vazio");
    }

    @Test
    void fimPrecisaSerDepoisDoInicio() {
        BannerRequestDTO dados = new BannerRequestDTO(null, null, null, null, "Vestido", true, AGORA, AGORA, null);

        assertThatThrownBy(() -> bannerService.criar(dados, imagem("d.jpg"), null))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("O fim da exibição precisa ser depois do início");
    }

    @Test
    void imagemDeDesktopEObrigatoriaEPrecisaSerFoto() {
        assertThatThrownBy(() -> bannerService.criar(dados("Sem imagem"), null, null))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage("Envie a imagem para computador no campo imagemDesktop");
        assertThatThrownBy(() -> bannerService.criar(dados("GIF"), imagem("d.jpg"),
                new MockMultipartFile("imagemCelular", "c.gif", "image/gif", GIF)))
                .isInstanceOf(ArquivoInvalidoException.class);
        assertThat(armazenamento.enviados).isEmpty();
    }

    @Test
    void edicaoMantemAsImagensQuandoNaoVemArquivo() {
        BannerAdminDTO criado = bannerService.criar(dados("Antes"), imagem("d.jpg"), imagem("c.jpg"));

        BannerAdminDTO editado = bannerService.atualizar(criado.id(), new BannerRequestDTO("Depois", null, null,
                "/categoria/vestido", "Vestido", false, AGORA.plusDays(1), null, null), null, null);

        assertThat(editado.titulo()).isEqualTo("Depois");
        assertThat(editado.link()).isEqualTo("/categoria/vestido");
        assertThat(editado.situacao()).isEqualTo(SituacaoBanner.DESATIVADO);
        assertThat(editado.imagemDesktop()).isEqualTo(criado.imagemDesktop());
        assertThat(editado.imagemCelular()).isEqualTo(criado.imagemCelular());
        assertThat(armazenamento.enviados).hasSize(2);
    }

    @Test
    void edicaoTrocaOuTiraAImagemDeCelular() {
        BannerAdminDTO criado = bannerService.criar(dados("Banner"), imagem("d.jpg"), imagem("c.jpg"));

        BannerAdminDTO trocado = bannerService.atualizar(criado.id(), dados("Banner"), null, imagem("c2.jpg"));
        assertThat(trocado.imagemCelular().url()).endsWith("rosie/banners/teste-3.jpg");

        BannerAdminDTO semCelular = bannerService.atualizar(criado.id(),
                new BannerRequestDTO(null, null, null, null, "Banner", true, null, null, true), null, null);
        assertThat(semCelular.imagemCelular().url()).contains("g_center").endsWith("rosie/banners/teste-1.jpg");
        assertThat(bannerRepository.findById(criado.id()).orElseThrow().getImagemCelularPublicId()).isNull();
    }

    @Test
    void reordenaComTodosOsBanners() {
        Banner a = salvar("A", 0, true, null, null);
        Banner b = salvar("B", 1, true, null, null);
        Banner c = salvar("C", 2, true, null, null);

        List<BannerAdminDTO> nova = bannerService.reordenar(List.of(c.getId(), a.getId(), b.getId()));

        assertThat(nova).extracting(BannerAdminDTO::titulo, BannerAdminDTO::ordem).containsExactly(
                tuple("C", 0),
                tuple("A", 1),
                tuple("B", 2));
        entityManager.clear();
        assertThat(bannerService.listarNoAr()).extracting(BannerDTO::titulo).containsExactly("C", "A", "B");
    }

    @Test
    void reordenarRecusaListaIncompletaOuRepetida() {
        Banner a = salvar("A", 0, true, null, null);
        Banner b = salvar("B", 1, true, null, null);

        for (List<Long> ids : List.of(List.of(a.getId()), List.of(a.getId(), a.getId()), List.of(a.getId(), b.getId(), 999L))) {
            assertThatThrownBy(() -> bannerService.reordenar(ids))
                    .isInstanceOf(DadosInvalidosException.class)
                    .hasMessage("Envie todos os banners, cada um uma vez");
        }
    }

    @Test
    void removerRenumeraOsOutros() {
        Banner a = salvar("A", 0, true, null, null);
        Banner b = salvar("B", 1, true, null, null);
        Banner c = salvar("C", 2, true, null, null);

        bannerService.remover(b.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(bannerService.listarAdmin()).extracting(BannerAdminDTO::id, BannerAdminDTO::ordem).containsExactly(
                tuple(a.getId(), 0),
                tuple(c.getId(), 1));
    }

    @Test
    void desativarTiraDaHome() {
        Banner a = salvar("A", 0, true, null, null);

        BannerAdminDTO desativado = bannerService.alterarAtivo(a.getId(), false);

        assertThat(desativado.situacao()).isEqualTo(SituacaoBanner.DESATIVADO);
        assertThat(bannerService.listarNoAr()).isEmpty();
    }

    @Test
    void bannerInexistenteResponde404() {
        assertThatThrownBy(() -> bannerService.remover(999_999L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> bannerService.alterarAtivo(999_999L, true)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> bannerService.atualizar(999_999L, dados("X"), null, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private Banner salvar(String titulo, int ordem, boolean ativo, LocalDateTime inicio, LocalDateTime fim) {
        return bannerRepository.saveAndFlush(Banner.builder()
                .titulo(titulo)
                .textoAlternativo("Banner " + titulo)
                .imagemDesktopUrl("https://placehold.co/1920x640")
                .ativo(ativo)
                .inicio(inicio)
                .fim(fim)
                .ordem(ordem)
                .build());
    }

    static BannerRequestDTO dados(String titulo) {
        return new BannerRequestDTO(titulo, null, null, null, "Banner " + titulo, true, null, null, null);
    }

    static MockMultipartFile imagem(String nome) {
        return new MockMultipartFile("imagem", nome, "image/jpeg", JPG);
    }
}
