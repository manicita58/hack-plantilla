package com.hackplantilla.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Un solo test, pero levanta el contexto completo: valida el pom, la config,
 * que Flyway aplique las migraciones y que `ddl-auto: validate` encuentre el
 * esquema que la entidad espera. Si algo de eso se desincroniza, falla acá y no
 * en producción.
 *
 * Necesita un Postgres en localhost:5432 (el CI lo levanta como service).
 */
@SpringBootTest
class HackPlantillaApplicationTests {

    @Autowired
    private ItemRepository repository;

    @Test
    void guardaYLeeUnItem() {
        Item guardado = repository.save(new Item("ping"));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getCreatedAt()).isNotNull();
        assertThat(repository.findById(guardado.getId()))
                .get()
                .extracting(Item::getName)
                .isEqualTo("ping");
    }
}
