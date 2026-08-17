package com.audittrove.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Expo Push servisine bildirim gonderir. Hata olsa da inceleme akisini bozmaz (sessiz loglar).
 */
@Component
public class ExpoPushClient {
    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);
    private static final String ENDPOINT = "https://exp.host/--/api/v2/push/send";
    private final RestClient client = RestClient.create();

    public void send(String expoToken, String title, String body) {
        if (expoToken == null || expoToken.isBlank()) {
            return;
        }
        try {
            client.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "to", expoToken,
                            "title", title,
                            "body", body,
                            "sound", "default"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Expo push gonderilemedi: {}", e.getMessage());
        }
    }
}