package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Troca de e-mail pedida na Minha Conta (#61) e ainda não confirmada: o login continua com o e-mail antigo até
// a cliente abrir o link mandado ao endereço novo. Uma por cliente (um pedido novo substitui o anterior). Do
// token do link só fica o hash SHA-256: quem lê o banco não consegue confirmar a troca.
@Entity
@Table(name = "email_pendente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailPendente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    // Já normalizado (sem espaços nas pontas, em minúsculas), como no cadastro
    @Column(name = "novo_email", nullable = false, length = 150)
    private String novoEmail;

    // SHA-256 do token em hexadecimal
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // No fuso da loja (Clock do RelogioConfig)
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "solicitado_em", nullable = false)
    private LocalDateTime solicitadoEm;
}
