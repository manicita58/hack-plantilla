package com.hackplantilla.ai.domain;

/**
 * Un pedazo de documento indexado. `score` es la similitud coseno contra la
 * pregunta (1 = idéntico); viene en null cuando no salió de una búsqueda.
 */
public record Chunk(Long id, String documentTitle, String content, Double score) {
}
