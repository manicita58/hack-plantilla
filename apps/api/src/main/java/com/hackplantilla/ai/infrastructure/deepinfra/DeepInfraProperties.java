package com.hackplantilla.ai.infrastructure.deepinfra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepInfra habla el protocolo de OpenAI, así que este adaptador sirve igual
 * para cualquier proveedor compatible (OpenRouter, Together, vLLM, Ollama con
 * su modo OpenAI): se cambia `base-url` y listo.
 */
@ConfigurationProperties(prefix = "ai.deepinfra")
public record DeepInfraProperties(
        String apiKey,
        String baseUrl,
        String chatModel,
        String embeddingModel,
        Integer maxTokens,
        Double temperature) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    void requireKey() {
        if (!configured()) {
            throw new IllegalStateException(
                    "falta DEEPINFRA_API_KEY: sacá una en https://deepinfra.com/dash/api_keys y ponela en el .env");
        }
    }
}
