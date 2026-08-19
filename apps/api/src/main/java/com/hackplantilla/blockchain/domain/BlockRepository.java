package com.hackplantilla.blockchain.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida. El dominio dice qué necesita; que sea Postgres, Mongo o una
 * lista en memoria es problema de infrastructure.
 */
public interface BlockRepository {

    /** Los bloques sellados, en orden de cadena. */
    List<Block> findChain();

    /** El último bloque sellado: de ahí sale el prevHash del próximo. */
    Optional<Block> findLast();

    Block save(Block block);
}
