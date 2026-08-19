package com.hackplantilla.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackplantilla.ai.domain.ChatMessage;
import com.hackplantilla.ai.domain.ChatModel;
import com.hackplantilla.ai.domain.ConversationStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * El chat se testea sin red y sin base: los puertos se reemplazan por dobles de
 * cuatro líneas. Esa es toda la ganancia de tener DeepInfra detrás de una
 * interfaz en vez de un RestClient metido en el servicio.
 */
class ChatServiceTest {

    private final ModeloFalso modelo = new ModeloFalso();
    private final MemoriaFalsa memoria = new MemoriaFalsa();
    private final ChatService chat = new ChatService(modelo, memoria, "sos un test", 2);

    @Test
    void mandaSystemPromptEHistorialYGuardaLosDosTurnos() {
        UUID conversacion = UUID.randomUUID();
        memoria.append(conversacion, ChatMessage.user("hola"));
        memoria.append(conversacion, ChatMessage.assistant("qué tal"));

        List<String> emitidos = new ArrayList<>();
        String respuesta = chat.ask(conversacion, "¿y ahora?", emitidos::add);

        assertThat(respuesta).isEqualTo("hola mundo");
        assertThat(emitidos).containsExactly("hola ", "mundo");   // llegó token a token

        assertThat(modelo.recibido.getFirst().role()).isEqualTo("system");
        assertThat(modelo.recibido).extracting(ChatMessage::content)
                .containsExactly("sos un test", "hola", "qué tal", "¿y ahora?");

        assertThat(memoria.history(conversacion)).hasSize(4);
    }

    @Test
    void recortaElHistorialALosUltimosTurnos() {
        UUID conversacion = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            memoria.append(conversacion, ChatMessage.user("pregunta " + i));
            memoria.append(conversacion, ChatMessage.assistant("respuesta " + i));
        }

        chat.ask(conversacion, "última", token -> { });

        // system + 2 turnos (4 mensajes) + la pregunta nueva: sin el recorte se
        // le mandarían los 20 mensajes y la cuenta de tokens se dispara.
        assertThat(modelo.recibido).hasSize(6);
    }

    private static final class ModeloFalso implements ChatModel {
        private List<ChatMessage> recibido = List.of();

        @Override
        public String complete(List<ChatMessage> messages) {
            recibido = List.copyOf(messages);
            return "hola mundo";
        }

        @Override
        public void stream(List<ChatMessage> messages, Consumer<String> onToken) {
            recibido = List.copyOf(messages);
            onToken.accept("hola ");
            onToken.accept("mundo");
        }
    }

    private static final class MemoriaFalsa implements ConversationStore {
        private final Map<UUID, List<ChatMessage>> porConversacion = new HashMap<>();

        @Override
        public List<ChatMessage> history(UUID conversationId) {
            return List.copyOf(porConversacion.getOrDefault(conversationId, List.of()));
        }

        @Override
        public void append(UUID conversationId, ChatMessage message) {
            porConversacion.computeIfAbsent(conversationId, id -> new ArrayList<>()).add(message);
        }
    }
}
