package com.hackplantilla.ai.application;

import com.hackplantilla.ai.AiModule;
import com.hackplantilla.ai.domain.ChatMessage;
import com.hackplantilla.ai.domain.ChatModel;
import com.hackplantilla.ai.domain.ConversationStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Caso de uso: conversar con memoria. Guarda cada turno y le manda al modelo el
 * historial recortado a las últimas N interacciones (más contexto = más tokens
 * = más plata y más latencia).
 */
@Service
@AiModule
public class ChatService {

    private final ChatModel model;
    private final ConversationStore conversations;
    private final String systemPrompt;
    private final int historyTurns;

    public ChatService(ChatModel model,
                       ConversationStore conversations,
                       @Value("${ai.system-prompt}") String systemPrompt,
                       @Value("${ai.history-turns}") int historyTurns) {
        this.model = model;
        this.conversations = conversations;
        this.systemPrompt = systemPrompt;
        this.historyTurns = historyTurns;
    }

    public List<ChatMessage> history(UUID conversationId) {
        return conversations.history(conversationId);
    }

    /**
     * Manda el mensaje y va emitiendo tokens. Devuelve la respuesta completa,
     * que además queda guardada en el historial.
     */
    public String ask(UUID conversationId, String message, Consumer<String> onToken) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("el mensaje no puede estar vacío");
        }

        List<ChatMessage> previos = conversations.history(conversationId);
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(systemPrompt));
        prompt.addAll(previos.subList(Math.max(0, previos.size() - historyTurns * 2), previos.size()));
        prompt.add(ChatMessage.user(message));

        StringBuilder completa = new StringBuilder();
        model.stream(prompt, token -> {
            completa.append(token);
            onToken.accept(token);
        });

        // Se guarda al final y en orden: si el modelo falla a mitad, la
        // conversación no queda con una pregunta sin respuesta.
        conversations.append(conversationId, ChatMessage.user(message));
        conversations.append(conversationId, ChatMessage.assistant(completa.toString()));
        return completa.toString();
    }
}
