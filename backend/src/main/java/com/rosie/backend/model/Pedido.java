package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id", nullable = false)
    private Endereco endereco;

    // VARCHAR e não ENUM do MySQL: com ENUM, cada status novo exigiria ALTER TABLE (o ddl-auto=update não
    // acrescenta valores a um ENUM existente)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatusPedido status = StatusPedido.AGUARDANDO_PAGAMENTO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    @Column(name = "valor_frete", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorFrete = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    // Frete escolhido no checkout, cotado de novo no servidor (#24). servicoFreteId é o id do serviço no
    // Melhor Envio, que a etiqueta (#25) usa para comprar o envio.
    @Column(length = 50)
    private String transportadora;

    @Column(name = "servico_frete", length = 50)
    private String servicoFrete;

    @Column(name = "servico_frete_id")
    private Integer servicoFreteId;

    @Column(name = "prazo_frete_dias")
    private Integer prazoFreteDias;

    @Column(name = "codigo_rastreio", length = 50)
    private String codigoRastreio;

    // Mercado Pago (#24): a preferência do Checkout Pro, o link de pagamento e o pagamento aprovado
    @Column(name = "preferencia_id", length = 100)
    private String preferenciaId;

    @Column(name = "url_pagamento", length = 500)
    private String urlPagamento;

    @Column(name = "pagamento_id", length = 50)
    private String pagamentoId;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    // Por que o pedido ficou PAGO_COM_PENDENCIA, para a Roseane resolver
    @Column(length = 500)
    private String pendencia;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PedidoItem> itens = new ArrayList<>();
}
