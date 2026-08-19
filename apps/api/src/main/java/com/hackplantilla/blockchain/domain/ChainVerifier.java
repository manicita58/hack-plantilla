package com.hackplantilla.blockchain.domain;

import java.util.List;

/**
 * Recalcula la cadena entera desde el génesis. No confía en ningún hash
 * guardado: los vuelve a computar y compara.
 */
public final class ChainVerifier {

    private ChainVerifier() {
    }

    public static Verification verify(List<Block> blocks) {
        String prev = ChainHasher.GENESIS;
        for (Block block : blocks) {
            // Dos fallas distintas: `enlazado` detecta filas borradas, insertadas
            // o reordenadas; `intacto` detecta contenido editado.
            boolean enlazado = prev.equals(block.prevHash());
            boolean intacto = ChainHasher.hash(prev, block.content(), block.createdAt()).equals(block.hash());
            if (!enlazado || !intacto) {
                return new Verification(false, blocks.size(), block.id());
            }
            prev = block.hash();
        }
        return new Verification(true, blocks.size(), null);
    }
}
