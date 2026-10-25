package com.rosie.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Token de uso único dos links mandados por e-mail: confirmação da troca de e-mail (#61) e redefinição de
// senha (#93). O token só vai no link; no banco fica o hash.
final class TokenLink {

    // 256 bits aleatórios, em Base64 sem "=" e seguro para URL (43 caracteres)
    private static final int BYTES_TOKEN = 32;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenLink() {
    }

    static String novo() {
        byte[] bytes = new byte[BYTES_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 basta (sem sal nem BCrypt): o token já é aleatório e longo, não dá para adivinhar por tentativa
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
