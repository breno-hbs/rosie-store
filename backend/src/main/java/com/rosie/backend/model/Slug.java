package com.rosie.backend.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// Endereço da categoria na loja (/categoria/{slug}, #88), gerado do nome: sem acentos, em minúsculas e com
// tudo que não é letra ou número virando um hífen só ("Cropped/Body" → "cropped-body", "Calça" → "calca").
// Usado também pela migração V3, que preencheu as categorias que já existiam.
public final class Slug {

    // Tamanho da coluna: o nome tem até 80 caracteres, e sobra espaço para o sufixo de repetido ("-2")
    public static final int TAMANHO_MAXIMO = 90;
    private static final int TAMANHO_BASE = 80;
    // Nome sem nenhuma letra ou número (ex.: só emoji)
    private static final String RESERVA = "categoria";

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");
    private static final Pattern HIFENS_DAS_PONTAS = Pattern.compile("^-+|-+$");

    private Slug() {
    }

    public static String de(String nome) {
        String semAcentos = MARCAS.matcher(Normalizer.normalize(nome, Normalizer.Form.NFD)).replaceAll("");
        String slug = SEPARADORES.matcher(semAcentos.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = HIFENS_DAS_PONTAS.matcher(slug).replaceAll("");
        if (slug.length() > TAMANHO_BASE) {
            slug = HIFENS_DAS_PONTAS.matcher(slug.substring(0, TAMANHO_BASE)).replaceAll("");
        }
        return slug.isEmpty() ? RESERVA : slug;
    }

    // Candidato número n para um slug já usado por outra categoria: "vestido", "vestido-2", "vestido-3"...
    public static String candidato(String base, int numero) {
        return numero <= 1 ? base : base + "-" + numero;
    }
}
