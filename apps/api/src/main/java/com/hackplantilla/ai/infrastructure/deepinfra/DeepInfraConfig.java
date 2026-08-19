package com.hackplantilla.ai.infrastructure.deepinfra;

import com.hackplantilla.ai.AiModule;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@AiModule
@EnableConfigurationProperties(DeepInfraProperties.class)
class DeepInfraConfig {

    @Bean
    RestClient deepInfraRestClient(DeepInfraProperties props) {
        // Read timeout largo a propósito: generar 800 tokens puede tardar más de
        // 30s y el default cortaría la respuesta a la mitad.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(180));

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + (props.apiKey() == null ? "" : props.apiKey()))
                .build();
    }
}
