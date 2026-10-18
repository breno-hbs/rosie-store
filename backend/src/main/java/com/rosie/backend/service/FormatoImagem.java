package com.rosie.backend.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// Reconhece JPG, PNG e WEBP pelos primeiros bytes do arquivo (assinatura do formato)
final class FormatoImagem {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private FormatoImagem() {
    }

    static boolean aceito(byte[] conteudo) {
        return comecaCom(conteudo, 0, JPG)
                || comecaCom(conteudo, 0, PNG)
                // WEBP: "RIFF" + 4 bytes de tamanho + "WEBP"
                || (comecaCom(conteudo, 0, RIFF) && comecaCom(conteudo, 8, WEBP));
    }

    private static boolean comecaCom(byte[] conteudo, int inicio, byte[] assinatura) {
        return conteudo.length >= inicio + assinatura.length
                && Arrays.equals(conteudo, inicio, inicio + assinatura.length, assinatura, 0, assinatura.length);
    }
}
