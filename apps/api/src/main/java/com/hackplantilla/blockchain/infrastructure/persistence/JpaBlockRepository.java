package com.hackplantilla.blockchain.infrastructure.persistence;

import com.hackplantilla.blockchain.BlockchainModule;
import com.hackplantilla.blockchain.domain.Block;
import com.hackplantilla.blockchain.domain.BlockRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adaptador de salida: traduce entre el dominio y JPA, y nada más. */
@Repository
@BlockchainModule
class JpaBlockRepository implements BlockRepository {

    private final LedgerEntryJpaRepository jpa;

    JpaBlockRepository(LedgerEntryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Block> findChain() {
        return jpa.findByHashIsNotNullOrderByIdAsc().stream().map(LedgerEntryEntity::toDomain).toList();
    }

    @Override
    public Optional<Block> findLast() {
        return jpa.findFirstByHashIsNotNullOrderByIdDesc().map(LedgerEntryEntity::toDomain);
    }

    @Override
    public Block save(Block block) {
        return jpa.save(LedgerEntryEntity.from(block)).toDomain();
    }
}
