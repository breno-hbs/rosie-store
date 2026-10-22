package com.rosie.backend.service;

import com.rosie.backend.exception.DadosInvalidosException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

// Link do banner (#89): só páginas do próprio site, para o banner nunca levar a cliente para fora da loja
// sem aviso. Aceita o caminho ("/categoria/vestido", "/produtos/12", "/busca?q=linho") ou a URL completa da
// loja (ROSIE_LOJA_URL, com ou sem "www."), que vira caminho. URL de outro site, "//outro.site",
// "javascript:" e afins são recusados com 400 DADOS_INVALIDOS.
@Component
public class LinkBanner {

    static final String MENSAGEM = "O link do banner precisa ser uma página da loja, como /categoria/vestido, "
            + "/produtos/12 ou /busca?q=linho";

    // Caminho começando por uma barra só, com os caracteres de URL que não mudam o destino (sem "\", espaço,
    // aspas nem "<>"); query e âncora opcionais. Acentos e espaços precisam vir codificados (%20).
    private static final Pattern CAMINHO = Pattern.compile(
            "/(?![/\\\\])[A-Za-z0-9\\-._~%!$&'()*+,;=:@/]*(\\?[A-Za-z0-9\\-._~%!$&'()*+,;=:@/?]*)?(#[A-Za-z0-9\\-._~%/?]*)?");

    private final String hostLoja;

    public LinkBanner(@Value("${rosie.loja.url}") String urlLoja) {
        this.hostLoja = semWww(hostDe(urlLoja));
    }

    // Link pronto para gravar: nulo se veio vazio, sempre um caminho começando com "/"
    public String validar(String link) {
        if (link == null || link.isBlank()) return null;
        String limpo = link.strip();

        String caminho = limpo.startsWith("/") ? limpo : caminhoDaLoja(limpo);
        if (caminho == null || !CAMINHO.matcher(caminho).matches()) {
            throw new DadosInvalidosException(MENSAGEM);
        }
        return caminho;
    }

    // "https://rosiestore.com.br/produtos/12?x=1" → "/produtos/12?x=1"; outro site ou outro esquema → null
    private String caminhoDaLoja(String url) {
        try {
            URI uri = new URI(url);
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!esquema.equals("https") && !esquema.equals("http")) return null;
            if (uri.getRawUserInfo() != null || hostLoja == null || !hostLoja.equals(semWww(hostComPorta(uri)))) {
                return null;
            }
            String caminho = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            if (uri.getRawQuery() != null) caminho += "?" + uri.getRawQuery();
            if (uri.getRawFragment() != null) caminho += "#" + uri.getRawFragment();
            return caminho;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String hostDe(String url) {
        try {
            return hostComPorta(new URI(url.strip()));
        } catch (URISyntaxException | NullPointerException e) {
            return null;
        }
    }

    private static String hostComPorta(URI uri) {
        if (uri.getHost() == null) return null;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return uri.getPort() < 0 ? host : host + ":" + uri.getPort();
    }

    private static String semWww(String host) {
        if (host == null) return null;
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
