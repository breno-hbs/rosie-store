package com.rosie.backend.service;

import com.rosie.backend.model.Enquadramento;
import com.rosie.backend.model.ProdutoImagem;

// URL com que as fotos saem da API (#18, RNF09): o Cloudinary guarda a foto original e entrega
// sempre a versão 4:5 pela transformação na URL. Assim nenhuma foto fica fora da proporção e o
// enquadramento pode mudar depois sem reenviar nada. Único lugar que monta essa URL.
public final class UrlEntregaImagem {

    static final String MARCADOR = "/image/upload/";
    // Corta em 4:5 na resolução original, reduz para no máximo 1000 px de largura (sem ampliar
    // foto pequena) e deixa o Cloudinary escolher formato e qualidade para cada navegador
    static final String CORTE = "c_fill,ar_4:5,%s/c_limit,w_1000/f_auto,q_auto/";

    private UrlEntregaImagem() {
    }

    public static String de(ProdutoImagem imagem) {
        return montar(imagem.getUrl(), imagem.getPublicId(), imagem.getEnquadramento());
    }

    // Imagens antigas (placeholders, sem public_id) não estão no Cloudinary: saem como foram salvas
    public static String montar(String url, String publicId, Enquadramento enquadramento) {
        if (url == null || publicId == null) return url;
        int posicao = url.indexOf(MARCADOR);
        if (posicao < 0) return url;

        int inicio = posicao + MARCADOR.length();
        return url.substring(0, inicio) + CORTE.formatted(gravidade(enquadramento)) + url.substring(inicio);
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
