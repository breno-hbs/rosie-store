package com.rosie.backend.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugTest {

    @Test
    void tiraAcentosEPoeEmMinusculas() {
        assertThat(Slug.de("Calça")).isEqualTo("calca");
        assertThat(Slug.de("Acessórios Ávila ÇÃO")).isEqualTo("acessorios-avila-cao");
    }

    @Test
    void barraEEspacosViramUmHifenSo() {
        assertThat(Slug.de("Cropped/Body")).isEqualTo("cropped-body");
        assertThat(Slug.de("Blusa   de  frio")).isEqualTo("blusa-de-frio");
        assertThat(Slug.de("Cropped / Body")).isEqualTo("cropped-body");
        assertThat(Slug.de("Saia -- Longa")).isEqualTo("saia-longa");
    }

    @Test
    void semHifenNasPontas() {
        assertThat(Slug.de("  /Vestido!  ")).isEqualTo("vestido");
    }

    @Test
    void mantemNumeros() {
        assertThat(Slug.de("Kit 2 peças")).isEqualTo("kit-2-pecas");
    }

    @Test
    void nomeSemLetraNemNumeroViraCategoria() {
        assertThat(Slug.de("★ ★")).isEqualTo("categoria");
    }

    @Test
    void nomeLongoCortaEmOitentaSemHifenNoFim() {
        assertThat(Slug.de("a".repeat(79) + " bcd")).isEqualTo("a".repeat(79));
        assertThat(Slug.de("b".repeat(100))).hasSize(80);
    }

    @Test
    void candidatoNumeraAPartirDoDois() {
        assertThat(Slug.candidato("vestido", 1)).isEqualTo("vestido");
        assertThat(Slug.candidato("vestido", 2)).isEqualTo("vestido-2");
        assertThat(Slug.candidato("vestido", 3)).isEqualTo("vestido-3");
    }
}
