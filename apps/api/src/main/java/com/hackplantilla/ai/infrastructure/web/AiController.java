package com.hackplantilla.ai.infrastructure.web;

import tools.jackson.databind.ObjectMapper;
import com.hackplantilla.ai.AiModule;
import com.hackplantilla.ai.application.ChatService;
import com.hackplantilla.ai.application.RagService;
import com.hackplantilla.ai.domain.ChatMessage;
import com.hackplantilla.ai.domain.ChunkStore;
import com.hackplantilla.ai.infrastructure.deepinfra.DeepInfraProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Adaptador de entrada del módulo de IA.
 *
 *   POST /ai/chat        chat con streaming (SSE) e historial por conversación
 *   POST /ai/documents   indexa un texto para el RAG
 *   POST /ai/ask         pregunta contra los documentos indexados
 */
@RestController
@RequestMapping("/ai")
@AiModule
public class AiController {

    private final ChatService chat;
    private final RagService rag;
    private final DeepInfraProperties props;
    private final ObjectMapper json;

    // Hilos virtuales (Java 21+): cada stream abierto ocupa un hilo bloqueado
    // esperando tokens, y con hilos de plataforma 200 chats simultáneos se
    // comerían el pool entero.
    private final ExecutorService streams = Executors.newVirtualThreadPerTaskExecutor();

    public AiController(ChatService chat, RagService rag, DeepInfraProperties props, ObjectMapper json) {
        this.chat = chat;
        this.rag = rag;
        this.props = props;
        this.json = json;
    }

    /** Para que el front sepa si el módulo está usable antes de dejar escribir. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "configured", props.configured(),
                "chatModel", props.chatModel(),
                "embeddingModel", props.embeddingModel(),
                "documents", rag.documents());
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest body) {
        UUID conversationId = body.conversationId() == null ? UUID.randomUUID() : body.conversationId();
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());

        streams.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("start").data(evento("conversationId", conversationId.toString())));
                chat.ask(conversationId, body.message(), token -> enviar(emitter, token));
                emitter.send(SseEmitter.event().name("done").data(evento("conversationId", conversationId.toString())));
                emitter.complete();
            } catch (Exception e) {
                // El error viaja como evento y no como status: para cuando falla,
                // el 200 del SSE ya salió y el front no puede leer otro código.
                try {
                    emitter.send(SseEmitter.event().name("error").data(evento("message", mensaje(e))));
                } catch (IOException ignored) {
                    // el cliente ya cerró: no hay a quién avisarle
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    @GetMapping("/conversations/{id}")
    public List<ChatMessage> history(@PathVariable UUID id) {
        return chat.history(id);
    }

    @PostMapping("/documents")
    public RagService.Ingestion ingest(@RequestBody Document body) {
        return rag.ingest(body.title(), body.content());
    }

    @GetMapping("/documents")
    public List<ChunkStore.DocumentSummary> documents() {
        return rag.documents();
    }

    @DeleteMapping("/documents/{title}")
    public Map<String, Integer> forget(@PathVariable String title) {
        return Map.of("deleted", rag.forget(title));
    }

    @PostMapping("/ask")
    public RagService.Answer ask(@RequestBody Question body) {
        return rag.ask(body.question());
    }

    /** Cada token va como JSON de una línea: un token con \n rompería el formato SSE. */
    private void enviar(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(evento("t", token)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String evento(String clave, String valor) {
        // Jackson 3 tira excepciones unchecked: no hace falta envolverlo.
        return json.writeValueAsString(Map.of(clave, valor));
    }

    private static String mensaje(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    public record ChatRequest(UUID conversationId, String message) {
    }

    public record Document(String title, String content) {
    }

    public record Question(String question) {
    }
}
