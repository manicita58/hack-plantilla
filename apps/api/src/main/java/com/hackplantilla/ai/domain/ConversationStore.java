package com.hackplantilla.ai.domain;

import java.util.List;
import java.util.UUID;

/** Puerto de salida al historial de conversaciones. */
public interface ConversationStore {

    List<ChatMessage> history(UUID conversationId);

    void append(UUID conversationId, ChatMessage message);
}
