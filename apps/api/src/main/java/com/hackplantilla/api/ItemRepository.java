package com.hackplantilla.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    /** Los bloques, en orden. Los items sin hash son anteriores a la cadena. */
    List<Item> findByHashIsNotNullOrderByIdAsc();

    /** El último bloque: de ahí sale el prevHash del próximo. */
    Optional<Item> findFirstByHashIsNotNullOrderByIdDesc();
}
