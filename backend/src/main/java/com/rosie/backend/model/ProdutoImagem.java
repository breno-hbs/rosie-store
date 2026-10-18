package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "produto_imagem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoImagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, length = 500)
    private String url;

    // Id da foto no Cloudinary, para apagá-la de lá quando sair do produto. Nulo nas imagens
    // cadastradas antes do upload (#15), que não estão no Cloudinary.
    @Column(name = "public_id")
    private String publicId;

    // Parte da foto que fica no corte 4:5 da entrega (#18). Nulo nas fotos enviadas antes, que
    // ficam no automático.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Enquadramento enquadramento;

    @Column(nullable = false)
    @Builder.Default
    private Integer ordem = 0;
}
