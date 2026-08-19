package com.hackplantilla.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hackplantilla.ai.domain.TextChunker;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void parteConSolapeYSinPerderTexto() {
        // Cada caracter es el dígito de su posición: así se ve exactamente
        // dónde corta cada chunk.
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            texto.append(i % 10);
        }

        List<String> chunks = TextChunker.split(texto.toString(), 100, 20);

        assertThat(chunks).hasSize(3);                        // 0-100, 80-180, 160-250
        assertThat(chunks.get(0)).isEqualTo(texto.substring(0, 100));
        // Los 20 caracteres de solape: el chunk 2 arranca antes de que termine el 1.
        assertThat(chunks.get(1)).isEqualTo(texto.substring(80, 180));
        assertThat(chunks.get(2)).isEqualTo(texto.substring(160, 250));
    }

    @Test
    void unTextoCortoEsUnSoloChunk() {
        assertThat(TextChunker.split("hola mundo", 900, 150)).containsExactly("hola mundo");
    }

    @Test
    void textoVacioNoProduceChunks() {
        assertThat(TextChunker.split("   ", 900, 150)).isEmpty();
    }

    @Test
    void solapeMayorQueElTamanoEsUnError() {
        assertThatThrownBy(() -> TextChunker.split("x", 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
