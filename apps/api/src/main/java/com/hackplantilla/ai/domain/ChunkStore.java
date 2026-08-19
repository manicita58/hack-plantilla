package com.hackplantilla.ai.domain;

import java.util.List;

/** Puerto de salida al índice vectorial. */
public interface ChunkStore {

    void save(String documentTitle, List<String> contents, List<float[]> embeddings);

    /** Los k chunks más parecidos a la pregunta, del más parecido al menos. */
    List<Chunk> search(float[] question, int k);

    List<DocumentSummary> documents();

    int deleteDocument(String documentTitle);

    record DocumentSummary(String title, int chunks) {
    }
}
