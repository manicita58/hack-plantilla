package com.hackplantilla.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Cadena de bloques mínima sobre la misma tabla `items`: el hash de cada bloque
 * incluye el hash del anterior, así que tocar una fila vieja (o borrarla, o
 * reordenarla) rompe todos los hashes siguientes y `/chain/verify` lo canta.
 *
 * No hay red, ni consenso, ni firmas: es un ledger a prueba de manipulación
 * dentro de tu propia DB, que es lo que una demo necesita el 99% de las veces.
 */
@Service
public class Chain {

    /** El primer bloque no tiene anterior: apunta al bloque cero. */
    public static final String GENESIS = "0".repeat(64);

    private final ItemRepository repository;

    public Chain(ItemRepository repository) {
        this.repository = repository;
    }

    /**
     * Agrega un bloque al final de la cadena.
     *
     * ponytail: synchronized alcanza para un proceso. Con varias réplicas dos
     * escrituras simultáneas pueden leer el mismo último bloque y bifurcar la
     * cadena; ahí hace falta un lock en la DB (SELECT ... FOR UPDATE del último).
     */
    public synchronized Item append(String name) {
        String prev = repository.findFirstByHashIsNotNullOrderByIdDesc()
                .map(Item::getHash)
                .orElse(GENESIS);

        Item block = new Item(name);
        block.seal(prev, hash(prev, name, block.getCreatedAt()));
        return repository.save(block);
    }

    /** Recalcula toda la cadena y devuelve dónde se rompió, si se rompió. */
    public Verification verify() {
        List<Item> blocks = repository.findByHashIsNotNullOrderByIdAsc();

        String prev = GENESIS;
        for (Item block : blocks) {
            boolean enlazado = prev.equals(block.getPrevHash());
            boolean intacto = hash(prev, block.getName(), block.getCreatedAt()).equals(block.getHash());
            if (!enlazado || !intacto) {
                return new Verification(false, blocks.size(), block.getId());
            }
            prev = block.getHash();
        }
        return new Verification(true, blocks.size(), null);
    }

    static String hash(String prevHash, String name, Instant createdAt) {
        String payload = prevHash + "|" + name + "|" + createdAt;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("toda JVM trae SHA-256", e);
        }
    }

    /** `brokenAt` es el id del primer bloque que no cuadra, o null si está sana. */
    public record Verification(boolean valid, int blocks, Long brokenAt) {
    }
}
