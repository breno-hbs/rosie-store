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

    // Etiqueta de volta do Melhor Envio (logística reversa, #63), só quando o frete é da Rosie (defeito): o id
    // do envio lá (gravado assim que entra no carrinho, para nunca comprar duas vezes), o link público para a
    // cliente imprimir, o rastreio e quando ficou pronta. erroEtiquetaVolta guarda o motivo da última
    // tentativa que falhou, para o painel mostrar; some quando a etiqueta sai.
    @Column(name = "envio_volta_id", length = 50)
    private String envioVoltaId;

    @Column(name = "url_etiqueta_volta", length = 500)
    private String urlEtiquetaVolta;

    @Column(name = "codigo_rastreio_volta", length = 50)
    private String codigoRastreioVolta;

    @Column(name = "etiqueta_volta_gerada_em")
    private LocalDateTime etiquetaVoltaGeradaEm;

    @Column(name = "erro_etiqueta_volta", length = 500)
    private String erroEtiquetaVolta;

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

    // Em que pé está a etiqueta de volta (#63), como o painel mostra
    public SituacaoEtiquetaVolta getSituacaoEtiquetaVolta() {
        if (urlEtiquetaVolta != null) return SituacaoEtiquetaVolta.GERADA;
        if (getFreteConta() == FreteConta.CLIENTE) return SituacaoEtiquetaVolta.POR_CONTA_DA_CLIENTE;
        return erroEtiquetaVolta != null ? SituacaoEtiquetaVolta.FALHOU : SituacaoEtiquetaVolta.SEM_ETIQUETA;
    }
}
