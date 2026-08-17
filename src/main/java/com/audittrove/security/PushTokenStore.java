package com.audittrove.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * deviceId -> Expo push token esleme. Bellekte tutulur; mobil her acilista/is baslatista
 * yeniden kaydeder. Restart'ta kaybolur (kalicilik gerekirse device tablosuna tasinir).
 */
@Component
public class PushTokenStore {
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public void put(String deviceId, String token) {
        if (deviceId != null && token != null && !token.isBlank()) {
            tokens.put(deviceId, token);
        }
    }

    public String get(String deviceId) {
        return deviceId == null ? null : tokens.get(deviceId);
    }
}