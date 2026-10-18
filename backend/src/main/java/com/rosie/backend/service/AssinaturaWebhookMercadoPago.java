package com.rosie.backend.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

// Assinatura do webhook do Mercado Pago (cabeçalho x-signature: "ts=1704908010,v1=<hmac em hex>").
// O v1 é o HMAC-SHA256, com o segredo do webhook como chave, do manifesto
//   id:<data.id da URL>;request-id:<cabeçalho x-request-id>;ts:<ts do x-signature>;
// em que cada par ausente na notificação sai do manifesto. Mesmo algoritmo do WebhookSignatureValidator
// do SDK Java oficial (3.7.0), sem trazer o SDK inteiro. Doc:
// https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks
// Sem checagem de janela de tempo: um webhook repetido não faz mal, porque o status sempre vem da
// consulta ao Mercado Pago e o processamento é idempotente.
public final class AssinaturaWebhookMercadoPago {

    private AssinaturaWebhookMercadoPago() {
    }

    public static boolean valida(String xSignature, String xRequestId, String dataId, String segredo) {
        String assinatura = limpar(xSignature);
        if (assinatura == null || segredo == null || segredo.isBlank()) return false;

        String ts = null;
        String v1 = null;
        for (String parte : assinatura.split(",")) {
            int igual = parte.indexOf('=');
            if (igual <= 0) continue;
            String chave = parte.substring(0, igual).trim().toLowerCase();
            String valor = parte.substring(igual + 1).trim();
            if (chave.equals("ts")) ts = valor;
            else if (chave.equals("v1")) v1 = valor;
        }
        if (ts == null || !ts.matches("\\d+") || v1 == null || v1.isEmpty()) return false;

        String esperado = hmacHex(segredo, manifesto(limpar(dataId), limpar(xRequestId), ts));
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static String manifesto(String dataId, String requestId, String ts) {
        StringBuilder manifesto = new StringBuilder();
        if (dataId != null) manifesto.append("id:").append(dataId).append(';');
        if (requestId != null) manifesto.append("request-id:").append(requestId).append(';');
        return manifesto.append("ts:").append(ts).append(';').toString();
    }

    static String hmacHex(String segredo, String mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível na JVM", e);
        }
    }

    private static String limpar(String valor) {
        if (valor == null) return null;
        String aparado = valor.trim();
        return aparado.isEmpty() ? null : aparado;
    }
}
