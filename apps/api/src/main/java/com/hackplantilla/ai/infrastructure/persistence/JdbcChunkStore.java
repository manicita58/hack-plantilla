package com.hackplantilla.ai.infrastructure.persistence;

import com.hackplantilla.ai.AiModule;
import com.hackplantilla.ai.domain.Chunk;
import com.hackplantilla.ai.domain.ChunkStore;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Índice vectorial sobre pgvector. Sin servicio aparte: los embeddings viven en
 * el mismo Postgres que el resto, o sea una infra menos que levantar y un JOIN
 * posible entre tus datos y tus vectores.
 */
@Repository
@AiModule
class JdbcChunkStore implements ChunkStore {

    private final JdbcTemplate jdbc;

    JdbcChunkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String documentTitle, List<String> contents, List<float[]> embeddings) {
        if (contents.size() != embeddings.size()) {
            throw new IllegalArgumentException("un embedding por chunk, no más ni menos");
        }
        List<Object[]> filas = new ArrayList<>(contents.size());
        for (int i = 0; i < contents.size(); i++) {
            // Por índice y no por indexOf(): dos chunks idénticos en el mismo
            // documento apuntarían todos al primer embedding.
            filas.add(new Object[] { documentTitle, contents.get(i), literal(embeddings.get(i)) });
        }
        jdbc.batchUpdate("INSERT INTO ai_chunks (document_title, content, embedding) VALUES (?, ?, ?::vector)", filas);
    }

    @Override
    public List<Chunk> search(float[] question, int k) {
        String vector = literal(question);
        // `<=>` es distancia coseno en pgvector: 0 = idéntico. La similitud que
        // le muestro al front es 1 - distancia, que se lee mucho mejor.
        return jdbc.query("""
                        SELECT id, document_title, content, 1 - (embedding <=> ?::vector) AS score
                        FROM ai_chunks
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """,
                (rs, row) -> new Chunk(rs.getLong("id"), rs.getString("document_title"),
                        rs.getString("content"), rs.getDouble("score")),
                vector, vector, k);
    }

    @Override
    public List<DocumentSummary> documents() {
        return jdbc.query(
                "SELECT document_title, COUNT(*) AS chunks FROM ai_chunks GROUP BY document_title ORDER BY document_title",
                (rs, row) -> new DocumentSummary(rs.getString("document_title"), rs.getInt("chunks")));
    }

    @Override
    public int deleteDocument(String documentTitle) {
        return jdbc.update("DELETE FROM ai_chunks WHERE document_title = ?", documentTitle);
    }

    /** pgvector recibe el vector como texto `[0.1,0.2,…]` y lo castea con ?::vector. */
    private static String literal(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}
