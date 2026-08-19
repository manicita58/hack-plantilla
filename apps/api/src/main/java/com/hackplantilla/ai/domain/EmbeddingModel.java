package com.hackplantilla.ai.domain;

import java.util.List;

/** Puerto de salida al modelo de embeddings: texto -> vector. */
public interface EmbeddingModel {

    List<float[]> embed(List<String> texts);

    default float[] embedOne(String text) {
        return embed(List.of(text)).getFirst();
    }
}
