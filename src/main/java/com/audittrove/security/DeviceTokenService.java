package com.audittrove.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

// hmac imzali cihaz tokenlari, db gerektirmez
@Service
public class DeviceTokenService {

    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;

    public DeviceTokenService(@Value("${audittrove.security.token-secret:}") String secret) {
        this.secret = secret == null || secret.isBlank()
                ? null
                : secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isEnabled() {
        return secret != null;
    }

    public String issue(String deviceId) {
        String payload = deviceId + ":" + (System.currentTimeMillis() / 1000);
        return B64E.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + B64E.encodeToString(hmac(payload));
    }

    public Optional<String> verify(String token) {
        if (token == null || secret == null) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Optional.empty();
        try {
            byte[] payloadBytes = B64D.decode(token.substring(0, dot));
            byte[] givenSig = B64D.decode(token.substring(dot + 1));
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(hmac(payload), givenSig)) return Optional.empty();
            int sep = payload.lastIndexOf(':');
            if (sep <= 0) return Optional.empty();
            return Optional.of(payload.substring(0, sep));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC hesaplanamadı", e);
        }
    }
}