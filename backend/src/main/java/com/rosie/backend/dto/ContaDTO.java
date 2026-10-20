package com.rosie.backend.dto;

import com.rosie.backend.model.Usuario;

import java.time.LocalDateTime;

// Dados pessoais da cliente na Minha Conta (#26). O e-mail não muda pelo PUT /conta (é o login): a troca passa
// pelo PUT /conta/email e só vale depois da confirmação pelo link; enquanto isso, emailPendente (#61) diz para
// onde foi o link e até quando vale (nulo sem troca pendente ou com o link vencido). privacidadeVersao e
// privacidadeAceitaEm (#31) mostram qual versão da política ela aceitou e quando.
public record ContaDTO(
        Long id,
        String nome,
        String email,
        String telefone,
        String privacidadeVersao,
        LocalDateTime privacidadeAceitaEm,
        EmailPendenteDTO emailPendente
) {

    public static ContaDTO from(Usuario usuario, EmailPendenteDTO emailPendente) {
        return new ContaDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone(),
                usuario.getPrivacidadeVersao(), usuario.getPrivacidadeAceitaEm(), emailPendente);
    }
}
