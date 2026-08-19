package com.hackplantilla.blockchain.domain;

/**
 * `brokenAt` es el id del PRIMER bloque que no cuadra, no de todos: como el daño
 * se propaga hacia adelante, ese es el punto donde alguien metió mano.
 */
public record Verification(boolean valid, int blocks, Long brokenAt) {
}
