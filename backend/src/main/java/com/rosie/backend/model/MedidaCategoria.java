package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Uma linha da tabela de medidas de uma categoria (ex.: "Busto"), com um valor em cm por tamanho.
// Os valores são texto curto para aceitar faixas como "84–88"; vazio fica null.
@Entity
@Table(name = "categoria_medida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedidaCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 40)
    private String nome;

    // Posição da linha na tabela, a partir de 0
    @Column(nullable = false)
    private Integer ordem;

    @Column(name = "valor_p", length = 20)
    private String valorP;

    @Column(name = "valor_m", length = 20)
    private String valorM;

    @Column(name = "valor_g", length = 20)
    private String valorG;

    @Column(name = "valor_gg", length = 20)
    private String valorGG;

    public String getValor(Tamanho tamanho) {
        return switch (tamanho) {
            case P -> valorP;
            case M -> valorM;
            case G -> valorG;
            case GG -> valorGG;
        };
    }

    public void setValor(Tamanho tamanho, String valor) {
        switch (tamanho) {
            case P -> valorP = valor;
            case M -> valorM = valor;
            case G -> valorG = valor;
            case GG -> valorGG = valor;
        }
    }
}
