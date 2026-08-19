package com.hackplantilla.blockchain.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * El dominio se testea sin Spring, sin base y sin red: eso es lo que compra
 * tener la lógica en `domain` y no adentro del controller.
 */
class ChainVerifierTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T10:00:01Z");

    @Test
    void unaCadenaBienSelladaEsValida() {
        assertThat(ChainVerifier.verify(cadenaDeDos()).valid()).isTrue();
    }

    @Test
    void detectaContenidoEditado() {
        List<Block> cadena = cadenaDeDos();
        // Alguien editó la fila por psql: el hash guardado ya no corresponde.
        Block adulterado = new Block(1L, "ADULTERADO", T1, cadena.getFirst().prevHash(), cadena.getFirst().hash());

        Verification resultado = ChainVerifier.verify(List.of(adulterado, cadena.get(1)));

        assertThat(resultado.valid()).isFalse();
        assertThat(resultado.brokenAt()).isEqualTo(1L);
    }

    @Test
    void detectaUnBloqueBorradoDelMedio() {
        List<Block> cadena = cadenaDeTres();

        Verification resultado = ChainVerifier.verify(List.of(cadena.get(0), cadena.get(2)));

        // El tercero apunta al hash del segundo, que ya no está: se rompe ahí.
        assertThat(resultado.valid()).isFalse();
        assertThat(resultado.brokenAt()).isEqualTo(3L);
    }

    @Test
    void unaCadenaVaciaEsValida() {
        Verification resultado = ChainVerifier.verify(List.of());

        assertThat(resultado.valid()).isTrue();
        assertThat(resultado.blocks()).isZero();
    }

    private static List<Block> cadenaDeDos() {
        return cadenaDeTres().subList(0, 2);
    }

    private static List<Block> cadenaDeTres() {
        String h1 = ChainHasher.hash(ChainHasher.GENESIS, "uno", T1);
        String h2 = ChainHasher.hash(h1, "dos", T2);
        String h3 = ChainHasher.hash(h2, "tres", T2);
        return List.of(
                new Block(1L, "uno", T1, ChainHasher.GENESIS, h1),
                new Block(2L, "dos", T2, h1, h2),
                new Block(3L, "tres", T2, h2, h3));
    }
}
