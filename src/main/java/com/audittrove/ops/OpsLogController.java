package com.audittrove.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Ops servisinin log tamponunu okudugu uc.
 * Koruma: X-Ops-Secret basligi OPS_SECRET env degiskeniyle eslesmelidir.
 * OPS_SECRET tanimli degilse uc tamamen kapali davranir (404) — sessiz ve gorunmez.
 */
@RestController
@RequestMapping("/internal/ops")
public class OpsLogController {

    private final String opsSecret;

    public OpsLogController(@Value("${OPS_SECRET:}") String opsSecret) {
        this.opsSecret = opsSecret == null ? "" : opsSecret;
    }

    @GetMapping("/logs")
    public ResponseEntity<?> logs(
            @RequestHeader(value = "X-Ops-Secret", required = false) String secret,
            @RequestParam(value = "minutes", defaultValue = "35") int minutes,
            @RequestParam(value = "minLevel", defaultValue = "WARN") String minLevel) {

        if (opsSecret.isEmpty() || !constantTimeEquals(opsSecret, secret)) {
            return ResponseEntity.notFound().build();
        }
        int safeMinutes = Math.max(1, Math.min(minutes, 24 * 60));
        List<OpsLogBuffer.Entry> entries = OpsLogBuffer.snapshot(safeMinutes, minLevel);
        return ResponseEntity.ok(Map.of(
                "generatedAt", System.currentTimeMillis(),
                "minutes", safeMinutes,
                "minLevel", minLevel,
                "count", entries.size(),
                "entries", entries));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}