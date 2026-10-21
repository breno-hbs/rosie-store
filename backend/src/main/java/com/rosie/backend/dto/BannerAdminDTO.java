package com.rosie.backend.dto;

import com.rosie.backend.model.Banner;
import com.rosie.backend.model.SituacaoBanner;
import com.rosie.backend.service.UrlEntregaImagem;
import com.rosie.backend.service.UrlEntregaImagem.VersoesBanner;

import java.time.LocalDateTime;

// Banner no painel: o que a Home recebe (BannerDTO) mais o período, a ordem e a situação de agora.
// previaDesktop e previaCelular são as imagens inteiras, sem corte (previaCelular nula quando não há imagem
// de celular), para o painel desenhar a prévia de cada tela.
public record BannerAdminDTO(
        Long id,
        String titulo,
        String texto,
        String textoBotao,
        String link,
        String textoAlternativo,
        Boolean ativo,
        LocalDateTime inicio,
        LocalDateTime fim,
        Integer ordem,
        SituacaoBanner situacao,
        VersoesBanner imagemDesktop,
        VersoesBanner imagemCelular,
        String previaDesktop,
        String previaCelular
) {

    public static BannerAdminDTO from(Banner banner, LocalDateTime agora) {
        String previaCelular = banner.getImagemCelularUrl() == null
                ? null
                : UrlEntregaImagem.inteira(banner.getImagemCelularUrl(), banner.getImagemCelularPublicId());
        return new BannerAdminDTO(banner.getId(), banner.getTitulo(), banner.getTexto(), banner.getTextoBotao(),
                banner.getLink(), banner.getTextoAlternativo(), banner.getAtivo(), banner.getInicio(),
                banner.getFim(), banner.getOrdem(), SituacaoBanner.de(banner, agora), BannerDTO.desktop(banner),
                BannerDTO.celular(banner),
                UrlEntregaImagem.inteira(banner.getImagemDesktopUrl(), banner.getImagemDesktopPublicId()),
                previaCelular);
    }
}
