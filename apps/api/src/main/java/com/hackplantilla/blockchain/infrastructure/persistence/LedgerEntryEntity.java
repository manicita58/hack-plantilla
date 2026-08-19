package com.hackplantilla.blockchain.infrastructure.persistence;

import com.hackplantilla.blockchain.domain.Block;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * La tabla. Vive en infrastructure a propósito: el dominio no sabe que existe
 * JPA, así que se puede reusar el hexágono con otra persistencia sin tocarlo.
 */
@Entity
@Table(name = "ledger_entries")
class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Nulos en filas anteriores a la cadena: esas no son bloques y se ignoran.
    @Column(name = "prev_hash")
    private String prevHash;

    @Column(name = "hash")
    private String hash;

    protected LedgerEntryEntity() {
        // requerido por JPA
    }

    static LedgerEntryEntity from(Block block) {
        LedgerEntryEntity entity = new LedgerEntryEntity();
        entity.id = block.id();
        entity.content = block.content();
        entity.createdAt = block.createdAt();
        entity.prevHash = block.prevHash();
        entity.hash = block.hash();
        return entity;
    }

    Block toDomain() {
        return new Block(id, content, createdAt, prevHash, hash);
    }
}
