package com.audittrove.llm;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Claude (Anthropic Messages API) uzerinden ikincil inceleme. */
@Component
public class AnthropicBackend implements SecondaryBackend {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public AnthropicBackend(RestClient.Builder builder,
                            @Value("${ANTHROPIC_API_KEY:}") String apiKey,
                            @Value("${AUDITTROVE_ANTHROPIC_MODEL:claude-sonnet-5}") String model,
                            @Value("${audittrove.openai.timeout-seconds:90}") int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = builder.baseUrl("https://api.anthropic.com").requestFactory(requestFactory).build();
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public boolean configured() {
        return !apiKey.isBlank();
    }

    @Override
    public String completeJson(String systemContent, String userContent) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 8192,
                "system", systemContent,
                "messages", List.of(Map.of("role", "user", "content", userContent)));
        JsonNode response = restClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return response == null ? "" : response.at("/content/0/text").asText("");
    }
}