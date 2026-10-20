package com.rosie.backend.dto;

import com.rosie.backend.model.EmailPendente;

import java.time.LocalDateTime;

// Troca de e-mail esperando a confirmação pelo link (#61): para onde foi o link e até quando ele vale
public record EmailPendenteDTO(String novoEmail, LocalDateTime expiraEm) {

    public static EmailPendenteDTO from(EmailPendente pendente) {
        return new EmailPendenteDTO(pendente.getNovoEmail(), pendente.getExpiraEm());
    }
}
