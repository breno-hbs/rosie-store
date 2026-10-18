package com.rosie.backend.service;

import com.rosie.backend.model.Enquadramento;
import com.rosie.backend.model.ProdutoImagem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// URL de entrega das fotos em 4:5 (#18): função pura, sem Spring nem rede
class UrlEntregaImagemTest {

    static final String ORIGINAL = "https://res.cloudinary.com/rosie/image/upload/v1729260000/rosie/produtos/abc123.jpg";

    @Test
    void cortaEm4por5ComLimiteDe1000pxEFormatoAutomatico() {
        assertThat(UrlEntregaImagem.montar(ORIGINAL, "rosie/produtos/abc123", Enquadramento.AUTOMATICO))
                .isEqualTo("https://res.cloudinary.com/rosie/image/upload/"
                        + "c_fill,ar_4:5,g_auto/c_limit,w_1000/f_auto,q_auto/v1729260000/rosie/produtos/abc123.jpg");
    }

    @Test
    void enquadramentoVaiNaGravidadeDoCorte() {
        assertThat(UrlEntregaImagem.montar(ORIGINAL, "rosie/produtos/abc123", Enquadramento.CENTRO))
                .contains("/c_fill,ar_4:5,g_center/");
        assertThat(UrlEntregaImagem.montar(ORIGINAL, "rosie/produtos/abc123", Enquadramento.TOPO))
                .contains("/c_fill,ar_4:5,g_north/");
    }

    @Test
    void semEnquadramentoGuardadoFicaNoAutomatico() {
        assertThat(UrlEntregaImagem.montar(ORIGINAL, "rosie/produtos/abc123", null))
                .isEqualTo(UrlEntregaImagem.montar(ORIGINAL, "rosie/produtos/abc123", Enquadramento.AUTOMATICO));
    }

    @Test
    void imagemAntigaSemPublicIdSaiComoFoiSalva() {
        assertThat(UrlEntregaImagem.montar("https://placehold.co/1000x1250?text=antiga", null, null))
                .isEqualTo("https://placehold.co/1000x1250?text=antiga");
        // mesmo uma URL do Cloudinary cadastrada à mão: sem public_id não é foto do upload
        assertThat(UrlEntregaImagem.montar(ORIGINAL, null, Enquadramento.TOPO)).isEqualTo(ORIGINAL);
    }

    @Test
    void urlForaDoFormatoDoCloudinarySaiComoEsta() {
        assertThat(UrlEntregaImagem.montar("https://outro.cdn/foto.jpg", "foto", Enquadramento.AUTOMATICO))
                .isEqualTo("https://outro.cdn/foto.jpg");
        assertThat(UrlEntregaImagem.montar(null, "foto", Enquadramento.AUTOMATICO)).isNull();
    }

    @Test
    void montaAPartirDaImagemSalva() {
        ProdutoImagem imagem = ProdutoImagem.builder()
                .url(ORIGINAL)
                .publicId("rosie/produtos/abc123")
                .enquadramento(Enquadramento.TOPO)
                .build();

        assertThat(UrlEntregaImagem.de(imagem))
                .isEqualTo(UrlEntregaImagem.montar(ORIGINAL, "rosie/produtos/abc123", Enquadramento.TOPO));
    }
}
