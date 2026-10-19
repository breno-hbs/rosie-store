package com.rosie.backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

// POST /conta/privacidade (#31): aceite da versão em vigor por quem aceitou uma antiga
public record AceitePrivacidadeRequestDTO(
        @NotNull(message = "Aceite a política de privacidade para continuar")
        @AssertTrue(message = "Aceite a política de privacidade para continuar")
        Boolean aceitePrivacidade
) {
}
