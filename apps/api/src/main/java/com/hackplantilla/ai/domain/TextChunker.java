package com.hackplantilla.ai.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Parte un documento en pedazos con solape. El solape existe para que una idea
 * que cae justo en el borde no quede partida al medio en los dos chunks.
 */
public final class TextChunker {

    private TextChunker() {
    }

    public static List<String> split(String text, int size, int overlap) {
        if (size <= overlap) {
            throw new IllegalArgumentException("el solape tiene que ser menor que el tamaño del chunk");
        }
        String limpio = text.strip().replaceAll("\\s+\n", "\n");
        if (limpio.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int paso = size - overlap;
        for (int inicio = 0; inicio < limpio.length(); inicio += paso) {
            String chunk = limpio.substring(inicio, Math.min(inicio + size, limpio.length())).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (inicio + size >= limpio.length()) {
                break;
            }
        }
        return chunks;
    }
}
