package com.rosie.backend.dto;

// privacidadePendente (#31): a cliente aceitou uma versão antiga da política de privacidade (ou nenhuma) e
// a loja pede o aceite da versão em vigor antes de seguir. Sempre false no admin.
public record LoginResponseDTO(String token, UsuarioResumoDTO usuario, boolean privacidadePendente) {
}
