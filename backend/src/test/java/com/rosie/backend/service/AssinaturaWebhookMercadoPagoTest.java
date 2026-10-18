package com.rosie.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Assinatura x-signature do webhook. Os hashes esperados foram calculados fora do Java, com
// printf '<manifesto>' | openssl dgst -sha256 -hmac 'segredo-de-teste'
class AssinaturaWebhookMercadoPagoTest {

    private static final String SEGREDO = "segredo-de-teste";
    private static final String REQUEST_ID = "2066ca19-c6f1-498a-be75-1923005edd06";
    private static final String TS = "1742505638683";
    // id:123456789;request-id:2066ca19-c6f1-498a-be75-1923005edd06;ts:1742505638683;
    private static final String HASH = "fa2204691df93ba67c56e4ca915b98862d717d633a83def16c6eaceafcdf4515";
    // id:123456789;ts:1742505638683;
    private static final String HASH_SEM_REQUEST_ID = "9ca6014805006c1bb1965883d153b33bd1e234cb4bd5f33b790773d76b6997a2";

    @Test
    void aceitaAAssinaturaCalculadaComOSegredo() {
        assertThat(AssinaturaWebhookMercadoPago.valida("ts=" + TS + ",v1=" + HASH, REQUEST_ID, "123456789", SEGREDO))
                .isTrue();
        // Espaços e ordem das partes não importam
        assertThat(AssinaturaWebhookMercadoPago.valida(" v1=" + HASH + ", ts=" + TS, REQUEST_ID, "123456789", SEGREDO))
                .isTrue();
    }

    @Test
    void semXRequestIdOParSaiDoManifesto() {
        assertThat(AssinaturaWebhookMercadoPago.manifesto("123456789", null, TS)).isEqualTo("id:123456789;ts:" + TS + ";");
        assertThat(AssinaturaWebhookMercadoPago.valida("ts=" + TS + ",v1=" + HASH_SEM_REQUEST_ID, null, "123456789", SEGREDO))
                .isTrue();
    }

    @Test
    void recusaAssinaturaQueNaoBate() {
        String valida = "ts=" + TS + ",v1=" + HASH;
        // Outro pagamento, outro request-id, outro ts, outro segredo
        assertThat(AssinaturaWebhookMercadoPago.valida(valida, REQUEST_ID, "123456780", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhookMercadoPago.valida(valida, "outro-request", "123456789", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhookMercadoPago.valida("ts=1742505638684,v1=" + HASH, REQUEST_ID, "123456789", SEGREDO))
                .isFalse();
        assertThat(AssinaturaWebhookMercadoPago.valida(valida, REQUEST_ID, "123456789", "outro-segredo")).isFalse();
    }

    @Test
    void recusaCabecalhoAusenteOuMalformado() {
        assertThat(AssinaturaWebhookMercadoPago.valida(null, REQUEST_ID, "123456789", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhookMercadoPago.valida("", REQUEST_ID, "123456789", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhookMercadoPago.valida("lixo", REQUEST_ID, "123456789", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhookMercadoPago.valida("v1=" + HASH, REQUEST_ID, "123456789", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhookMercadoPago.valida("ts=" + TS, REQUEST_ID, "123456789", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhookMercadoPago.valida("ts=abc,v1=" + HASH, REQUEST_ID, "123456789", SEGREDO)).isFalse();
        // Sem segredo configurado nada passa
        assertThat(AssinaturaWebhookMercadoPago.valida("ts=" + TS + ",v1=" + HASH, REQUEST_ID, "123456789", ""))
                .isFalse();
    }
}
