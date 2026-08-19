package com.hackplantilla.blockchain.application;

import com.hackplantilla.blockchain.BlockchainModule;
import com.hackplantilla.blockchain.domain.Block;
import com.hackplantilla.blockchain.domain.BlockRepository;
import com.hackplantilla.blockchain.domain.ChainHasher;
import com.hackplantilla.blockchain.domain.ChainVerifier;
import com.hackplantilla.blockchain.domain.Verification;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

/** Caso de uso: agregar un bloque al final de la cadena y verificarla. */
@Service
@BlockchainModule
public class LedgerService {

    private final BlockRepository blocks;

    public LedgerService(BlockRepository blocks) {
        this.blocks = blocks;
    }

    public List<Block> chain() {
        return blocks.findChain();
    }

    /**
     * ponytail: synchronized alcanza para un proceso. Con varias réplicas dos
     * escrituras simultáneas pueden leer el mismo último bloque y bifurcar la
     * cadena; ahí hace falta un lock en la DB (SELECT ... FOR UPDATE del último).
     */
    public synchronized Block append(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("el contenido del bloque no puede estar vacío");
        }
        String prev = blocks.findLast().map(Block::hash).orElse(ChainHasher.GENESIS);

        // Truncado a microsegundos porque es lo que guarda TIMESTAMPTZ: si se
        // sella con nanos, lo que vuelve de la DB ya no da el mismo hash y la
        // verificación falla siempre.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Block sealed = new Block(null, content, now, prev, ChainHasher.hash(prev, content, now));
        return blocks.save(sealed);
    }

    public Verification verify() {
        return ChainVerifier.verify(blocks.findChain());
    }
}
