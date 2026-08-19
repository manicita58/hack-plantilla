package com.hackplantilla.blockchain.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, Long> {

    List<LedgerEntryEntity> findByHashIsNotNullOrderByIdAsc();

    Optional<LedgerEntryEntity> findFirstByHashIsNotNullOrderByIdDesc();
}
