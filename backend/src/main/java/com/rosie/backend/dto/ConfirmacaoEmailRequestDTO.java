package com.rosie.backend.dto;

// POST /conta/email/confirmar (#61): o token do link mandado ao e-mail novo. Ausente ou vazio responde como
// token inválido (400 TOKEN_INVALIDO), para a página de confirmação tratar tudo igual.
public record ConfirmacaoEmailRequestDTO(String token) {
}
