package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.CLIENTE;

    // Consentimento da LGPD dado no cadastro de cliente (RNF06): quando e em qual versão da
    // política de privacidade. Nulo no admin, que não se cadastra pela loja.
    @Column(name = "privacidade_aceita_em")
    private LocalDateTime privacidadeAceitaEm;

    @Column(name = "privacidade_versao", length = 20)
    private String privacidadeVersao;

    // Conta excluída pela cliente na Minha Conta (#31): nome, e-mail, telefone e endereços foram
    // anonimizados e o login não entra mais. Os pedidos ficam (obrigação fiscal). Nulo = conta ativa.
    @Column(name = "excluida_em")
    private LocalDateTime excluidaEm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
