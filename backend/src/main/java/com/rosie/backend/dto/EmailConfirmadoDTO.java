package com.rosie.backend.dto;

// Resposta da confirmação da troca de e-mail (#61): o e-mail que passou a valer no login
public record EmailConfirmadoDTO(String email) {
}
