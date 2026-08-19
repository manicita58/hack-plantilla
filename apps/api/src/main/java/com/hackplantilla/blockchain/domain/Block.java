package com.hackplantilla.blockchain.domain;

import java.time.Instant;

/**
 * Un bloque de la cadena. Sin JPA, sin Spring: es el dominio, se testea solo.
 * `prevHash` y `hash` son null mientras el bloque no está sellado.
 */
public record Block(Long id, String content, Instant createdAt, String prevHash, String hash) {

    public Block sealedWith(String prevHash, String hash) {
        return new Block(id, content, createdAt, prevHash, hash);
    }
}
