package com.rosie.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Body de POST /pedidos/{id}/pagamento-simulado (só com PAGAMENTO_SIMULADO=true)
public record PagamentoSimuladoRequestDTO(
        @NotNull(message = "Informe o status")
        @Pattern(regexp = "APROVADO|PENDENTE|RECUSADO|CANCELADO", message = "Status inválido: use APROVADO, PENDENTE, RECUSADO ou CANCELADO")
        String status
) {
}
