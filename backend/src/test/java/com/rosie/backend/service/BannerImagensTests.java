package com.rosie.backend.service;

import com.rosie.backend.dto.BannerAdminDTO;
import com.rosie.backend.dto.BannerRequestDTO;
import com.rosie.backend.exception.ArmazenamentoIndisponivelException;
import com.rosie.backend.exception.DadosInvalidosException;
import com.rosie.backend.repository.BannerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static com.rosie.backend.service.BannerServiceTests.dados;
import static com.rosie.backend.service.BannerServiceTests.imagem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Imagens dos banners no Cloudinary (falso) quando a transação termina (#89): sem @Transactional no teste,
// cada chamada ao service confirma de verdade. Os banners criados aqui são apagados no fim.
@SpringBootTest
@Import(ArmazenamentoImagensFalso.Config.class)
class BannerImagensTests {

    @Autowired
    private BannerService bannerService;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private ArmazenamentoImagensFalso armazenamento;

    private final List<Long> criados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        armazenamento.limpar();
    }

    @AfterEach
    void limparBanco() {
        bannerRepository.deleteAllById(criados.stream().filter(bannerRepository::existsById).toList());
    }

    @Test
    void removerApagaAsDuasImagensDepoisDoCommit() {
        BannerAdminDTO banner = criar(true);

        bannerService.remover(banner.id());

        assertThat(armazenamento.removidos).containsExactlyInAnyOrder("rosie/banners/teste-1", "rosie/banners/teste-2");
        assertThat(bannerRepository.existsById(banner.id())).isFalse();
    }

    @Test
    void trocarImagemApagaSoAAntiga() {
        BannerAdminDTO banner = criar(false);

        bannerService.atualizar(banner.id(), dados("Banner"), imagem("nova.jpg"), null);

        assertThat(armazenamento.removidos).containsExactly("rosie/banners/teste-1");
        assertThat(bannerRepository.findById(banner.id()).orElseThrow().getImagemDesktopPublicId())
                .isEqualTo("rosie/banners/teste-2");
    }

    @Test
    void tirarAImagemDeCelularApagaEla() {
        BannerAdminDTO banner = criar(true);

        bannerService.atualizar(banner.id(),
                new BannerRequestDTO(null, null, null, null, "Banner", true, null, null, true), null, null);

        assertThat(armazenamento.removidos).containsExactly("rosie/banners/teste-2");
    }

    @Test
    void edicaoRecusadaNaoApagaNada() {
        BannerAdminDTO banner = criar(false);

        assertThatThrownBy(() -> bannerService.atualizar(banner.id(),
                new BannerRequestDTO(null, null, null, "https://golpe.com", "Banner", true, null, null, null),
                imagem("nova.jpg"), null))
                .isInstanceOf(DadosInvalidosException.class);

        assertThat(armazenamento.enviados).hasSize(1);
        assertThat(armazenamento.removidos).isEmpty();
    }

    @Test
    void falhaNaSegundaImagemApagaAPrimeiraENaoCriaNada() {
        long antes = bannerRepository.count();
        armazenamento.falharNoEnvio = 2;

        assertThatThrownBy(() -> bannerService.criar(dados("Banner"), imagem("d.jpg"), imagem("c.jpg")))
                .isInstanceOf(ArmazenamentoIndisponivelException.class);

        assertThat(armazenamento.removidos).containsExactly("rosie/banners/teste-1");
        assertThat(bannerRepository.count()).isEqualTo(antes);
    }

    @Test
    void falhaDoCloudinaryAoApagarNaoImpedeARemocao() {
        BannerAdminDTO banner = criar(false);
        armazenamento.falharNaRemocao = true;

        bannerService.remover(banner.id());

        assertThat(bannerRepository.existsById(banner.id())).isFalse();
    }

    private BannerAdminDTO criar(boolean comCelular) {
        BannerAdminDTO banner = bannerService.criar(dados("Banner"), imagem("d.jpg"), comCelular ? imagem("c.jpg") : null);
        criados.add(banner.id());
        return banner;
    }
}
