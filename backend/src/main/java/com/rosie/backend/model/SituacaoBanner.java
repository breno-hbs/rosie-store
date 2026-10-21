package com.rosie.backend.model;

import java.time.LocalDateTime;

// Onde o banner está agora (#89), calculada pelo período e pelo "ativo"; não é gravada
public enum SituacaoBanner {
    NO_AR,
    AGENDADO,
    ENCERRADO,
    DESATIVADO;

    public static SituacaoBanner de(Banner banner, LocalDateTime agora) {
        if (!Boolean.TRUE.equals(banner.getAtivo())) return DESATIVADO;
        if (banner.getInicio() != null && banner.getInicio().isAfter(agora)) return AGENDADO;
        if (banner.getFim() != null && !banner.getFim().isAfter(agora)) return ENCERRADO;
        return NO_AR;
    }
}
