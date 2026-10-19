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
import java.time.LocalDate;
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

    // Etiqueta do Melhor Envio (#25): o id do envio lá (gravado assim que entra no carrinho, para nunca
    // comprar duas vezes), o link para imprimir e quando a etiqueta ficou pronta
    @Column(name = "melhor_envio_id", length = 50)
    private String envioId;

    @Column(name = "url_etiqueta", length = 500)
    private String urlEtiqueta;

    @Column(name = "etiqueta_gerada_em")
    private LocalDateTime etiquetaGeradaEm;

    @Column(name = "codigo_rastreio", length = 50)
    private String codigoRastreio;

    // Dia em que a peça chegou, marcado pela Roseane no painel (#27) ou informado pelo rastreio (#64): o prazo
    // de troca conta daqui
    @Column(name = "data_entrega")
    private LocalDate dataEntrega;

    // Quem confirmou a entrega (#64). null nos pedidos entregues antes dela, todos marcados à mão.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "origem_entrega", length = 20)
    private OrigemEntrega origemEntrega;

    // Última vez que a rotina do rastreio (#64) consultou este pedido no Melhor Envio: a rodada começa pelos
    // consultados há mais tempo, para nenhum pedido ficar sempre de fora do lote
    @Column(name = "rastreio_consultado_em")
    private LocalDateTime rastreioConsultadoEm;

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
