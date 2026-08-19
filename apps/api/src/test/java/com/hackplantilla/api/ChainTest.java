package com.hackplantilla.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * El check que importa: si alguien edita una fila por fuera de la API, la cadena
 * tiene que quedar rota. @Transactional para que el sabotaje se revierta y no
 * deje la DB local inválida.
 *
 * Necesita un Postgres en localhost:5432 (el CI lo levanta como service).
 */
@SpringBootTest
@Transactional
class ChainTest {

    @Autowired
    private Chain chain;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    @Test
    void detectaLaFilaAdulterada() {
        Item primero = chain.append("bloque uno");
        chain.append("bloque dos");

        assertThat(chain.verify().valid()).isTrue();

        jdbc.update("UPDATE items SET name = 'adulterado' WHERE id = ?", primero.getId());
        em.clear();   // si no, verify() lee el item viejo de la caché de Hibernate y "pasa"

        Chain.Verification rota = chain.verify();
        assertThat(rota.valid()).isFalse();
        assertThat(rota.brokenAt()).isEqualTo(primero.getId());
    }

    @Test
    void encadenaCadaBloqueConElAnterior() {
        Item uno = chain.append("a");
        Item dos = chain.append("b");

        assertThat(dos.getPrevHash()).isEqualTo(uno.getHash());
        assertThat(uno.getHash()).hasSize(64).isNotEqualTo(dos.getHash());
    }
}
