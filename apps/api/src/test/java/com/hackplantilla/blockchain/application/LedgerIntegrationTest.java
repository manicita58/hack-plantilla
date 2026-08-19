package com.hackplantilla.blockchain.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackplantilla.blockchain.domain.Block;
import com.hackplantilla.blockchain.domain.Verification;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * El check que importa: si alguien edita una fila por fuera de la API, la cadena
 * tiene que quedar rota. @Transactional para que el sabotaje se revierta y no
 * deje la base local inválida.
 *
 * Necesita el Postgres del proyecto (PostGIS + pgvector) en localhost:5432.
 */
@SpringBootTest
@Transactional
class LedgerIntegrationTest {

    @Autowired
    private LedgerService ledger;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    @Test
    void detectaLaFilaAdulterada() {
        Block primero = ledger.append("bloque uno");
        ledger.append("bloque dos");

        assertThat(ledger.verify().valid()).isTrue();

        jdbc.update("UPDATE ledger_entries SET content = 'adulterado' WHERE id = ?", primero.id());
        em.clear();   // si no, verify() lee el bloque viejo de la caché de Hibernate y "pasa"

        Verification rota = ledger.verify();
        assertThat(rota.valid()).isFalse();
        assertThat(rota.brokenAt()).isEqualTo(primero.id());
    }

    @Test
    void encadenaCadaBloqueConElAnterior() {
        Block uno = ledger.append("a");
        Block dos = ledger.append("b");

        assertThat(dos.prevHash()).isEqualTo(uno.hash());
        assertThat(uno.hash()).hasSize(64).isNotEqualTo(dos.hash());
    }
}
