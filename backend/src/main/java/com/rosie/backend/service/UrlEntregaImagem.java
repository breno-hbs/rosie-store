package com.rosie.backend.service;

import com.rosie.backend.model.Enquadramento;
import com.rosie.backend.model.ProdutoImagem;

import java.util.List;
import java.util.stream.Collectors;

// URL com que as fotos saem da API (#18, RNF09): o Cloudinary guarda a foto original e entrega
// sempre a versão 4:5 pela transformação na URL. Assim nenhuma foto fica fora da proporção e o
// enquadramento pode mudar depois sem reenviar nada. Único lugar que monta essa URL.
// Os banners da Home (#89) usam o mesmo caminho, com um corte por tela (ver CorteBanner).
public final class UrlEntregaImagem {

    static final String MARCADOR = "/image/upload/";
    // Corta em 4:5 na resolução original, reduz para no máximo 1000 px de largura (sem ampliar
    // foto pequena) e deixa o Cloudinary escolher formato e qualidade para cada navegador
    static final String CORTE = "c_fill,ar_4:5,%s/c_limit,w_1000/f_auto,q_auto/";
    // Banner: corte da tela, largura de uma das versões do srcset e formato/qualidade automáticos
    static final String CORTE_BANNER = "c_fill,ar_%s,%s/c_limit,w_%d/f_auto,q_auto/";
    // Imagem inteira, sem corte, para a prévia do painel
    static final String INTEIRA = "c_limit,w_1600/f_auto,q_auto/";

    // Proporção e larguras (px) das versões do banner no srcset; larguraPadrao é a do src (navegador sem srcset)
    public enum CorteBanner {
        // Faixa larga do computador (a partir de 768 px de tela)
        DESKTOP("3:1", List.of(768, 1280, 1920), 1280),
        // Retrato do celular, a mesma proporção das fotos de produto e do Instagram
        CELULAR("4:5", List.of(480, 750, 1080), 750);

        final String proporcao;
        final List<Integer> larguras;
        final int larguraPadrao;

        CorteBanner(String proporcao, List<Integer> larguras, int larguraPadrao) {
            this.proporcao = proporcao;
            this.larguras = larguras;
            this.larguraPadrao = larguraPadrao;
        }
    }

    // src padrão e srcset ("url 480w, url 750w, ...") de um banner; srcset nulo fora do Cloudinary
    public record VersoesBanner(String url, String srcset) {
    }

    private UrlEntregaImagem() {
    }

    public static String de(ProdutoImagem imagem) {
        return montar(imagem.getUrl(), imagem.getPublicId(), imagem.getEnquadramento());
    }

    // Imagens antigas (placeholders, sem public_id) não estão no Cloudinary: saem como foram salvas
    public static String montar(String url, String publicId, Enquadramento enquadramento) {
        return inserir(url, publicId, CORTE.formatted(gravidade(enquadramento)));
    }

    // Banner cortado para a tela. centro = true corta pelo meio (g_center: a imagem de desktop no celular,
    // quando falta a de celular); senão o Cloudinary acha a parte principal (g_auto).
    public static VersoesBanner banner(String url, String publicId, CorteBanner corte, boolean centro) {
        String gravidade = centro ? "g_center" : "g_auto";
        String padrao = inserir(url, publicId, CORTE_BANNER.formatted(corte.proporcao, gravidade, corte.larguraPadrao));
        if (padrao.equals(url)) return new VersoesBanner(url, null);

        String srcset = corte.larguras.stream()
                .map(largura -> inserir(url, publicId, CORTE_BANNER.formatted(corte.proporcao, gravidade, largura))
                        + " " + largura + "w")
                .collect(Collectors.joining(", "));
        return new VersoesBanner(padrao, srcset);
    }

    // Original reduzida, sem corte: o painel desenha a prévia de cada tela a partir dela
    public static String inteira(String url, String publicId) {
        return inserir(url, publicId, INTEIRA);
    }

    private static String inserir(String url, String publicId, String transformacao) {
        if (url == null || publicId == null) return url;
        int posicao = url.indexOf(MARCADOR);
        if (posicao < 0) return url;

        int inicio = posicao + MARCADOR.length();
        return url.substring(0, inicio) + transformacao + url.substring(inicio);
    }

    private static String gravidade(Enquadramento enquadramento) {
        if (enquadramento == null) return "g_auto";
        return switch (enquadramento) {
            case AUTOMATICO -> "g_auto";
            case CENTRO -> "g_center";
            case TOPO -> "g_north";
        };
    }
}
