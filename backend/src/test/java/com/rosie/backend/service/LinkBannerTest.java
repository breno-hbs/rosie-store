package com.rosie.backend.service;

import com.rosie.backend.exception.DadosInvalidosException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Link do banner (#89): só páginas da própria loja. Função pura, sem Spring.
class LinkBannerTest {

    private final LinkBanner linkBanner = new LinkBanner("https://rosiestore.com.br");

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/categoria/cropped-body",
            "/produtos/12",
            "/busca?q=vestido%20linho",
            "/busca?q=cal%C3%A7a",
            "/politica-de-privacidade",
            "/produtos/12#medidas",
    })
    void aceitaCaminhoDoSite(String link) {
        assertThat(linkBanner.validar(link)).isEqualTo(link);
    }

    @ParameterizedTest
    @CsvSource({
            "https://rosiestore.com.br/categoria/vestido, /categoria/vestido",
            "https://www.rosiestore.com.br/busca?q=linho, /busca?q=linho",
            "HTTPS://ROSIESTORE.COM.BR, /",
            "'  /produtos/3  ', /produtos/3",
    })
    void urlDaLojaViraCaminho(String link, String esperado) {
        assertThat(linkBanner.validar(link)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void linkVazioFicaNulo(String link) {
        assertThat(linkBanner.validar(link)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://outra-loja.com/promo",
            "https://rosiestore.com.br.golpe.com/categoria/vestido",
            "https://usuario@rosiestore.com.br/",
            "http://rosiestore.com.br:8080/",
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,oi",
            "//outra-loja.com",
            "/\\outra-loja.com",
            "https://rosiestore.com.br//outra-loja.com",
            "categoria/vestido",
            "/busca?q=vestido linho",
            "/produtos/1\"><script>",
            "mailto:contato@rosiestore.com.br",
    })
    void recusaLinkParaForaDaLojaOuMalformado(String link) {
        assertThatThrownBy(() -> linkBanner.validar(link))
                .isInstanceOf(DadosInvalidosException.class)
                .hasMessage(LinkBanner.MENSAGEM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173/produtos/3", "http://localhost:5173/produtos/3?x=1"})
    void lojaEmOutraPortaTambemConta(String link) {
        LinkBanner local = new LinkBanner("http://localhost:5173");
        assertThat(local.validar(link)).startsWith("/produtos/3");
    }
}
