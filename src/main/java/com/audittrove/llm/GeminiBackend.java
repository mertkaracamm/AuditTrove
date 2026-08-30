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

/** Gemini (Google Generative Language API) uzerinden ikincil inceleme. */
@Component
public class GeminiBackend implements SecondaryBackend {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiBackend(RestClient.Builder builder,
                         @Value("${GEMINI_API_KEY:}") String apiKey,
                         @Value("${AUDITTROVE_GEMINI_MODEL:gemini-2.5-flash}") String model,
                         @Value("${audittrove.openai.timeout-seconds:90}") int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = builder.baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory).build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean configured() {
        return !apiKey.isBlank();
    }

    @Override
    public String completeJson(String systemContent, String userContent) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemContent))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userContent)))),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "maxOutputTokens", 8192));
        JsonNode response = restClient.post()
                .uri("/v1beta/models/" + model + ":generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return response == null ? "" : response.at("/candidates/0/content/parts/0/text").asText("");
    }
}