package com.hackplantilla.ai.infrastructure.deepinfra;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hackplantilla.ai.AiModule;
import com.hackplantilla.ai.domain.ChatMessage;
import com.hackplantilla.ai.domain.ChatModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adaptador de salida al chat de DeepInfra (API compatible con OpenAI). */
@Component
@AiModule
class DeepInfraChatModel implements ChatModel {

    private final RestClient client;
    private final DeepInfraProperties props;
    private final ObjectMapper json;

    DeepInfraChatModel(RestClient deepInfraRestClient, DeepInfraProperties props, ObjectMapper json) {
        this.client = deepInfraRestClient;
        this.props = props;
        this.json = json;
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        props.requireKey();
        Completion respuesta = client.post()
                .uri("/chat/completions")
                .body(new Request(props.chatModel(), messages, false, props.maxTokens(), props.temperature()))
                .retrieve()
                .body(Completion.class);

        if (respuesta == null || respuesta.choices().isEmpty()) {
            throw new IllegalStateException("DeepInfra devolvió una respuesta vacía");
        }
        return respuesta.choices().getFirst().message().content();
    }

    @Override
    public void stream(List<ChatMessage> messages, Consumer<String> onToken) {
        props.requireKey();
        client.post()
                .uri("/chat/completions")
                .body(new Request(props.chatModel(), messages, true, props.maxTokens(), props.temperature()))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("DeepInfra respondió " + response.getStatusCode());
                    }
                    leerSse(response.getBody(), onToken);
                    return null;
                });
    }

    /**
     * El stream viene como SSE: líneas `data: {json}` y un `data: [DONE]` final.
     * Se parsea a mano en vez de con un cliente SSE porque son cuatro líneas y
     * evita arrastrar WebFlux entero al proyecto.
     */
    private void leerSse(java.io.InputStream body, Consumer<String> onToken) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (!linea.startsWith("data:")) {
                    continue;
                }
                String payload = linea.substring(5).strip();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                JsonNode delta = json.readTree(payload).path("choices").path(0).path("delta").path("content");
                if (!delta.isMissingNode() && !delta.isNull()) {
                    onToken.accept(delta.asText());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Request(String model, List<ChatMessage> messages, boolean stream,
                           Integer max_tokens, Double temperature) {
    }

    private record Completion(List<Choice> choices) {
        private record Choice(Message message) {
        }

        private record Message(String content) {
        }
    }
}
