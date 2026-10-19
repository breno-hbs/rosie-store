package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// Pedido de troca ou devolução de uma peça do pedido (RF18, RNF08, #27). A peça é o produto no tamanho
// comprado; quantidade é quantas unidades dela voltam (até a quantidade comprada).
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

    // VARCHAR e não ENUM do MySQL, como o status do pedido
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 5)
    private Tamanho tamanho;

    @Column(nullable = false)
    private Integer quantidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoTroca motivo;

    // O que a cliente contou (opcional)
    @Column(length = 1000)
    private String observacao;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatusTroca status = StatusTroca.SOLICITADA;

    // Recado da Roseane para a cliente ao aprovar, recusar ou concluir (opcional)
    @Column(length = 1000)
    private String resposta;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // RNF08: sai sempre do motivo, nunca do que o front-end mandar
    public FreteConta getFreteConta() {
        return FreteConta.para(motivo);
    }
}
