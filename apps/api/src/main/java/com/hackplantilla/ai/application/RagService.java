package com.hackplantilla.ai.application;

import com.hackplantilla.ai.AiModule;
import com.hackplantilla.ai.domain.ChatMessage;
import com.hackplantilla.ai.domain.ChatModel;
import com.hackplantilla.ai.domain.Chunk;
import com.hackplantilla.ai.domain.ChunkStore;
import com.hackplantilla.ai.domain.EmbeddingModel;
import com.hackplantilla.ai.domain.TextChunker;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RAG en tres pasos: partir el documento, vectorizarlo, y al preguntar buscar
 * los pedazos más parecidos y metérselos al modelo como contexto. Sin esto el
 * modelo contesta de memoria y alucina; con esto contesta de tus documentos y
 * te dice de cuál sacó cada cosa.
 */
@Service
@AiModule
public class RagService {

    private final EmbeddingModel embeddings;
    private final ChunkStore chunks;
    private final ChatModel model;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int topK;
    private final int maxChars;

    public RagService(EmbeddingModel embeddings,
                      ChunkStore chunks,
                      ChatModel model,
                      @Value("${ai.rag.chunk-size}") int chunkSize,
                      @Value("${ai.rag.chunk-overlap}") int chunkOverlap,
                      @Value("${ai.rag.top-k}") int topK,
                      @Value("${ai.rag.max-document-chars}") int maxChars) {
        this.embeddings = embeddings;
        this.chunks = chunks;
        this.model = model;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.topK = topK;
        this.maxChars = maxChars;
    }

    public Ingestion ingest(String title, String text) {
        if (title == null || title.isBlank() || text == null || text.isBlank()) {
            throw new IllegalArgumentException("hacen falta título y contenido");
        }
        // Un documento gigante son miles de llamadas de embedding en un request:
        // tarda minutos, cuesta plata y el cliente se cansa antes. Partilo vos.
        if (text.length() > maxChars) {
            throw new IllegalArgumentException(
                    "el documento tiene %d caracteres y el tope es %d: subilo por partes"
                            .formatted(text.length(), maxChars));
        }
        List<String> pedazos = TextChunker.split(text, chunkSize, chunkOverlap);
        if (pedazos.isEmpty()) {
            throw new IllegalArgumentException("el documento quedó vacío después de limpiarlo");
        }
        // Reindexar es borrar y volver a insertar: así no quedan chunks viejos
        // del mismo documento contaminando las búsquedas.
        chunks.deleteDocument(title);
        chunks.save(title, pedazos, embeddings.embed(pedazos));
        return new Ingestion(title, pedazos.size());
    }

    public Answer ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("la pregunta no puede estar vacía");
        }
        List<Chunk> contexto = chunks.search(embeddings.embedOne(question), topK);
        if (contexto.isEmpty()) {
            return new Answer("Todavía no hay documentos indexados. Subí uno en /ai/documents.", List.of());
        }

        String fuentes = contexto.stream()
                .map(c -> "[" + c.documentTitle() + "]\n" + c.content())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
                Respondé la pregunta usando SOLO el contexto de abajo. Si el contexto no alcanza,
                decí que no está en los documentos; no inventes. Citá entre corchetes el título del
                documento que usaste.

                Contexto:
                %s

                Pregunta: %s""".formatted(fuentes, question);

        String respuesta = model.complete(List.of(ChatMessage.user(prompt)));
        return new Answer(respuesta, contexto);
    }

    public List<ChunkStore.DocumentSummary> documents() {
        return chunks.documents();
    }

    public int forget(String title) {
        return chunks.deleteDocument(title);
    }

    public record Ingestion(String title, int chunks) {
    }

    public record Answer(String answer, List<Chunk> sources) {
    }
}
