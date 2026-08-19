package com.hackplantilla.ai.domain;

import java.util.List;
import java.util.function.Consumer;

/**
 * Puerto de salida al LLM. El dominio no sabe que atrás hay DeepInfra: cambiás
 * el adaptador por uno de Ollama o de otro proveedor y no se toca nada más.
 */
public interface ChatModel {

    /** Respuesta completa, de una. Para RAG, donde igual hay que esperar todo. */
    String complete(List<ChatMessage> messages);

    /** Respuesta token a token. Para el chat, donde la latencia percibida importa. */
    void stream(List<ChatMessage> messages, Consumer<String> onToken);
}
