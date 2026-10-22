package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Banner do carrossel da Home (#89), cadastrado pela Roseane no painel. As imagens ficam no Cloudinary,
// como as fotos dos produtos (#15); o corte de cada tela sai na URL de entrega (UrlEntregaImagem).
@Entity
@Table(name = "banner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80)
    private String titulo;

    @Column(length = 200)
    private String texto;

    @Column(name = "texto_botao", length = 30)
    private String textoBotao;

    // Sempre um caminho do próprio site ("/categoria/vestido"), validado por LinkBanner
    @Column(length = 300)
    private String link;

    // Descrição da imagem para leitor de tela (obrigatória)
    @Column(name = "texto_alternativo", nullable = false, length = 200)
    private String textoAlternativo;

    @Column(name = "imagem_desktop_url", nullable = false, length = 500)
    private String imagemDesktopUrl;

    @Column(name = "imagem_desktop_public_id")
    private String imagemDesktopPublicId;

    // Opcional: sem ela, o celular recebe a de desktop com corte central
    @Column(name = "imagem_celular_url", length = 500)
    private String imagemCelularUrl;

    @Column(name = "imagem_celular_public_id")
    private String imagemCelularPublicId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    // Período de exibição, no horário de São Paulo; nulo = sem limite daquele lado
    private LocalDateTime inicio;

    private LocalDateTime fim;

    // Posição no carrossel: 0, 1, 2... sem buracos
    @Column(nullable = false)
    @Builder.Default
    private Integer ordem = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
