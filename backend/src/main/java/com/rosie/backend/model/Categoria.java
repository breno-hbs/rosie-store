package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nome;

    // Endereço na loja: /categoria/{slug} (#88). O CategoriaService gera do nome ao criar e ao renomear,
    // com sufixo quando outra categoria já usa ("vestido-2").
    @Column(nullable = false, unique = true, length = Slug.TAMANHO_MAXIMO)
    private String slug;

    // Categoria salva sem passar pelo CategoriaService (ex.: nos testes): slug direto do nome
    @PrePersist
    void preencherSlug() {
        if (slug == null) {
            slug = Slug.de(nome);
        }
    }
}
