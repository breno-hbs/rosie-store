package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    // "Composição e cuidados" da Página de Produto; opcional, nula nos produtos que já existiam
    @Column(columnDefinition = "TEXT")
    private String composicao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    // Peso e medidas da peça embalada, para o frete e a etiqueta (#56). Opcionais, nulos nos produtos que
    // já existiam: o que estiver nulo usa o pacote padrão (rosie.frete.peca.*, ver PacotePeca)
    @Column(name = "peso_kg", precision = 5, scale = 3)
    private BigDecimal pesoKg;

    @Column(name = "altura_cm")
    private Integer alturaCm;

    @Column(name = "largura_cm")
    private Integer larguraCm;

    @Column(name = "comprimento_cm")
    private Integer comprimentoCm;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    // Marcado pela Roseane para aparecer nos destaques da Home (RF01). O default no banco
    // preenche os produtos que já existiam quando a coluna foi criada.
    @Column(nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean destaque = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ON DELETE CASCADE no banco -> espelhado aqui com cascade + orphanRemoval
    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProdutoImagem> imagens = new ArrayList<>();

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProdutoEstoque> estoque = new ArrayList<>();
}
