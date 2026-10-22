package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Slug que a categoria tinha antes de ser renomeada (#88): o link antigo (/categoria/vestido), já divulgado no
// Instagram ou no WhatsApp, continua abrindo a categoria, e a loja troca o endereço pelo atual. Se outra
// categoria passar a usar esse slug, ela fica com ele e o registro antigo sai.
@Entity
@Table(name = "categoria_slug_antigo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaSlugAntigo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = Slug.TAMANHO_MAXIMO)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;
}
