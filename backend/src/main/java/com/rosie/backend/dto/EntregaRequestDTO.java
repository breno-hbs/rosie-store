package com.rosie.backend.dto;

import java.time.LocalDate;

// Body (opcional) de POST /admin/pedidos/{id}/entregue (#27): o dia em que a peça chegou, se não foi hoje
public record EntregaRequestDTO(LocalDate dataEntrega) {
}
