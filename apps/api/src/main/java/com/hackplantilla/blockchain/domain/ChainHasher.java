package com.hackplantilla.blockchain.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** La función de hash de la cadena. Pura y estática: la misma entrada da siempre lo mismo. */
public final class ChainHasher {

    /** El primer bloque no tiene anterior: apunta al bloque cero. */
    public static final String GENESIS = "0".repeat(64);

    private ChainHasher() {
    }

    /**
     * Los separadores `|` no son decorativos: sin ellos ("ab","c") y ("a","bc")
     * producen el mismo payload, o sea el mismo hash. Ambigüedad de concatenación.
     */
    public static String hash(String prevHash, String content, Instant createdAt) {
        String payload = prevHash + "|" + content + "|" + createdAt;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("toda JVM trae SHA-256", e);
        }
    }
}
