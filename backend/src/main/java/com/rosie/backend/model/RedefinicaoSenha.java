package com.rosie.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Link de "esqueci minha senha" (#93) pedido e ainda não usado, da cliente ou do admin. Um por usuário: um
// pedido novo substitui o anterior, cujo link deixa de valer. Do token só fica o hash SHA-256, como na troca de
// e-mail (EmailPendente, #61): quem lê o banco não consegue redefinir a senha.
@Entity
@Table(name = "redefinicao_senha")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedefinicaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    // SHA-256 do token em hexadecimal
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // No fuso da loja (Clock do RelogioConfig)
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "solicitado_em", nullable = false)
    private LocalDateTime solicitadoEm;
}
