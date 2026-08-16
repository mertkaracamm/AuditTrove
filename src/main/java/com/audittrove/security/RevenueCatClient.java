package com.audittrove.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Abonelik durumunu RevenueCat'ten ceker. Key yoksa devre disi.
@Component
public class RevenueCatClient {

    private static final Logger log = LoggerFactory.getLogger(RevenueCatClient.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String ENTITLEMENT = "pro";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    private record CacheEntry(boolean pro, long expiresAtMillis) {}
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RevenueCatClient(ObjectMapper objectMapper,
                            @Value("${audittrove.security.revenuecat-api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.enabled = apiKey != null && !apiKey.isBlank();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.revenuecat.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(factory)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPro(String deviceId) {
        if (!enabled || deviceId == null || deviceId.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(deviceId);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.pro();
        }
        boolean pro = fetchIsPro(deviceId);
        cache.put(deviceId, new CacheEntry(pro, now + CACHE_TTL.toMillis()));
        if (cache.size() > 10_000) {
            cache.entrySet().removeIf(e -> e.getValue().expiresAtMillis() <= now);
        }
        return pro;
    }

    public void invalidate(String deviceId) {
        if (deviceId != null) {
            cache.remove(deviceId);
        }
    }

    private boolean fetchIsPro(String deviceId) {
        try {
            String body = restClient.get()
                    .uri("/subscribers/{id}", deviceId)
                    .retrieve()
                    .body(String.class);
            JsonNode entitlements = objectMapper.readTree(body)
                    .path("subscriber")
                    .path("entitlements");
            JsonNode pro = entitlements.path(ENTITLEMENT);
            if (pro.isMissingNode()) {
                return false;
            }
            JsonNode expires = pro.path("expires_date");
            if (expires.isNull() || expires.isMissingNode()) {
                return true;
            }
            return java.time.Instant.parse(expires.asText())
                    .isAfter(java.time.Instant.now());
        } catch (Exception e) {
            // RC'ye ulasamazsak pro degil say, istegi dusurme
            log.warn("RevenueCat sorgusu basarisiz (deviceId={}): {}", deviceId, e.getMessage());
            return false;
        }
    }
}