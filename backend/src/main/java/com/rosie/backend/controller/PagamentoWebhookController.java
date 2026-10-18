package com.rosie.backend.controller;

import com.rosie.backend.service.GatewayPagamento;
import com.rosie.backend.service.PagamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Webhook do Mercado Pago (#24): público no SecurityConfig, protegido pela assinatura x-signature.
// O aviso chega como POST /pagamentos/webhook?data.id=<id do pagamento>&type=payment. Responder 2xx diz ao
// Mercado Pago que recebemos; erro 5xx (gateway fora do ar) faz ele tentar de novo mais tarde.
@RestController
@RequiredArgsConstructor
@Slf4j
public class PagamentoWebhookController {

    private final GatewayPagamento gatewayPagamento;
    private final PagamentoService pagamentoService;

    @PostMapping("/pagamentos/webhook")
    public ResponseEntity<Void> receber(
            @RequestParam(name = "type", required = false) String tipo,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId) {
        // Outros tópicos (merchant_order...) e o formato antigo IPN (?topic=payment&id=...), que não vem
        // assinado, são só confirmados: o mesmo pagamento chega também como webhook assinado
        if (!"payment".equals(tipo) || dataId == null || dataId.isBlank()) {
            log.debug("Aviso do Mercado Pago ignorado: type={}, data.id={}", tipo, dataId);
            return ResponseEntity.ok().build();
        }

        gatewayPagamento.validarAssinatura(xSignature, xRequestId, dataId);
        pagamentoService.processar(dataId);
        return ResponseEntity.ok().build();
    }
}
