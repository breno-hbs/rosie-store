package com.rosie.backend.dto;

import com.rosie.backend.model.Banner;
import com.rosie.backend.service.UrlEntregaImagem;
import com.rosie.backend.service.UrlEntregaImagem.CorteBanner;
import com.rosie.backend.service.UrlEntregaImagem.VersoesBanner;

// Banner no carrossel da Home (GET /banners). As imagens já vêm cortadas para cada tela, com srcset:
// imagemDesktop em 3:1 (telas a partir de 768 px) e imagemCelular em 4:5; sem imagem de celular
// cadastrada, imagemCelular é a de desktop com corte central.
public record BannerDTO(
        Long id,
        String titulo,
        String texto,
        String textoBotao,
        String link,
        String textoAlternativo,
        VersoesBanner imagemDesktop,
        VersoesBanner imagemCelular
) {

    public static BannerDTO from(Banner banner) {
        return new BannerDTO(banner.getId(), banner.getTitulo(), banner.getTexto(), banner.getTextoBotao(),
                banner.getLink(), banner.getTextoAlternativo(), desktop(banner), celular(banner));
    }

    static VersoesBanner desktop(Banner banner) {
        return UrlEntregaImagem.banner(banner.getImagemDesktopUrl(), banner.getImagemDesktopPublicId(),
                CorteBanner.DESKTOP, false);
    }

    static VersoesBanner celular(Banner banner) {
        if (banner.getImagemCelularUrl() == null) {
            return UrlEntregaImagem.banner(banner.getImagemDesktopUrl(), banner.getImagemDesktopPublicId(),
                    CorteBanner.CELULAR, true);
        }
        return UrlEntregaImagem.banner(banner.getImagemCelularUrl(), banner.getImagemCelularPublicId(),
                CorteBanner.CELULAR, false);
    }
}
