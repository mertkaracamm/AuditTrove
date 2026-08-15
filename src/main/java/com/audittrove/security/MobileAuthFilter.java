package com.audittrove.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mobil denetim endpoint'i (/api/v1/audit) için Bearer token doğrulaması
 * ve cihaz başına saatlik rate limit.
 *
 * Kapsam bilinçli olarak dardır: /mcp, domain verification, actuator,
 * swagger ve diğer tüm endpoint'lere dokunmaz. Secret tanımlı değilse
 * filtre tamamen pasiftir (mevcut davranış korunur).
 */
@Component
public class MobileAuthFilter extends OncePerRequestFilter {

    private final DeviceTokenService tokenService;
    private final int limitPerHour;

    /** deviceId -> (pencere başlangıç saati, sayaç) */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long hourEpoch, AtomicInteger count) {}

    public MobileAuthFilter(
            DeviceTokenService tokenService,
            @Value("${audittrove.security.audit-rate-limit-per-hour:5}") int limitPerHour) {
        this.tokenService = tokenService;
        this.limitPerHour = limitPerHour;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(tokenService.isEnabled()
                && "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/audit".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ")
                ? header.substring(7)
                : null;

        var deviceId = tokenService.verify(token);
        if (deviceId.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Geçersiz veya eksik erişim token'ı.");
            return;
        }

        if (!allow(deviceId.get())) {
            reject(response, 429,
                    "Saatlik inceleme limitine ulaşıldı. Lütfen daha sonra tekrar deneyin.");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean allow(String deviceId) {
        long hour = System.currentTimeMillis() / 3_600_000L;
        Window w = windows.compute(deviceId, (k, cur) ->
                cur == null || cur.hourEpoch() != hour
                        ? new Window(hour, new AtomicInteger())
                        : cur);
        // Eski pencereleri ara sıra temizle (bellek büyümesin)
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().hourEpoch() != hour);
        }
        return w.count().incrementAndGet() <= limitPerHour;
    }

    private void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}