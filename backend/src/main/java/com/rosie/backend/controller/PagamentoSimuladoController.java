package com.rosie.backend.controller;

import com.rosie.backend.dto.PagamentoSimuladoRequestDTO;
import com.rosie.backend.dto.PedidoDTO;
import com.rosie.backend.service.GatewayPagamento.StatusPagamento;
import com.rosie.backend.service.PagamentoService;
import com.rosie.backend.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Só para desenvolvimento (PAGAMENTO_SIMULADO=true): a página /pedido/{id}/pagamento-simulado da loja aprova,
// recusa ou cancela o pagamento, que passa pelo mesmo processamento do webhook. Fora do modo simulado: 404.
@RestController
@RequiredArgsConstructor
public class PagamentoSimuladoController {

    private final PagamentoService pagamentoService;
    private final PedidoService pedidoService;

    @PostMapping("/pedidos/{id}/pagamento-simulado")
    public PedidoDTO simular(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                             @Valid @RequestBody PagamentoSimuladoRequestDTO requisicao) {
        Long clienteId = Long.valueOf(jwt.getSubject());
        pagamentoService.simular(clienteId, id, StatusPagamento.valueOf(requisicao.status()));
        return pedidoService.buscar(clienteId, id);
    }
}
