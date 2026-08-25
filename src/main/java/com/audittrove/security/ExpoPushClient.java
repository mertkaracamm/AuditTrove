package com.audittrove.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Expo Push servisine bildirim gonderir. Hata olsa da inceleme akisini bozmaz (sessiz loglar).
 * Not: Expo push API hatalari da HTTP 200 ile doner; gercek sonuc yanit govdesindedir
 * (or. DeviceNotRegistered, InvalidCredentials). Bu yuzden govde loglanir.
 */
@Component
public class ExpoPushClient {
    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);
    private static final String ENDPOINT = "https://exp.host/--/api/v2/push/send";
    private final RestClient client = RestClient.create();

    public void send(String expoToken, String title, String body) {
        if (expoToken == null || expoToken.isBlank()) {
            log.warn("Expo push atlandi: token bos");
            return;
        }
        try {
            String response = client.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "to", expoToken,
                            "title", title,
                            "body", body,
                            "sound", "default"))
                    .retrieve()
                    .body(String.class);
            if (response != null && response.contains("\"error\"")) {
                log.warn("Expo push hata yaniti: {}", response);
            } else {
                log.info("Expo push gonderildi, yanit: {}", response);
            }
        } catch (Exception e) {
            log.warn("Expo push gonderilemedi: {}", e.getMessage());
        }
    }
}
