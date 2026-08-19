package com.hackplantilla.ai.infrastructure.deepinfra;

import com.hackplantilla.ai.AiModule;
import com.hackplantilla.ai.domain.EmbeddingModel;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adaptador de salida al modelo de embeddings de DeepInfra. */
@Component
@AiModule
class DeepInfraEmbeddingModel implements EmbeddingModel {

    private final RestClient client;
    private final DeepInfraProperties props;

    DeepInfraEmbeddingModel(RestClient deepInfraRestClient, DeepInfraProperties props) {
        this.client = deepInfraRestClient;
        this.props = props;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        props.requireKey();
        if (texts.isEmpty()) {
            return List.of();
        }

        Response respuesta = client.post()
                .uri("/embeddings")
                .body(new Request(props.embeddingModel(), texts, "float"))
                .retrieve()
                .body(Response.class);

        if (respuesta == null || respuesta.data().size() != texts.size()) {
            throw new IllegalStateException("DeepInfra devolvió menos embeddings de los que se le pidieron");
        }

        // La API no garantiza el orden: viene un `index` por dato, hay que ordenar.
        return respuesta.data().stream()
                .sorted(Comparator.comparingInt(Item::index))
                .map(item -> {
                    float[] vector = new float[item.embedding().size()];
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] = item.embedding().get(i).floatValue();
                    }
                    return vector;
                })
                .toList();
    }

    private record Request(String model, List<String> input, String encoding_format) {
    }

    private record Response(List<Item> data) {
    }

    private record Item(int index, List<Double> embedding) {
    }
}
