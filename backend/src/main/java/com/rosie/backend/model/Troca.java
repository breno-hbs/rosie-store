package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * NOVA TABELA — não existia no schema original (rosie_schema.sql).
 * Necessária para RF18 (motivo da troca) e RNF08 (regra de quem paga o frete).
 * Adicionar o script SQL correspondente antes de rodar (ver anotação abaixo).
 */
@Entity
@Table(name = "troca")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Troca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoTroca motivo;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "SOLICITADA";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * RNF08: calculado automaticamente a partir do motivo, não é campo livre.
     * Chamar esse método no service ao criar a troca, não deixar o front-end decidir.
     */
    public String calcularFreteConta() {
        return motivo == MotivoTroca.DEFEITO ? "ROSIE" : "CLIENTE";
    }
}
