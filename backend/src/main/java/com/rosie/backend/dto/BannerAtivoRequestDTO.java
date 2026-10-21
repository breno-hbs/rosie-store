package com.rosie.backend.dto;

import jakarta.validation.constraints.NotNull;

// Body de PUT /admin/banners/{id}/ativo: liga ou desliga o banner sem mexer no resto
public record BannerAtivoRequestDTO(
        @NotNull(message = "Informe se o banner fica ativo")
        Boolean ativo
) {
}
