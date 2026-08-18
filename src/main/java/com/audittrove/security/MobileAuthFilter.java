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

// /api/v1/audit icin token dogrulama + rate limit. Diger endpointlere karismaz.
@Component
public class MobileAuthFilter extends OncePerRequestFilter {

    public static final String DEVICE_ID_ATTR = "audittrove.deviceId";
    public static final String QUOTA_DECISION_ATTR = "audittrove.quotaDecision";

    private final DeviceTokenService tokenService;
    private final QuotaService quotaService;
    private final int limitPerHour;

    /** deviceId -> (pencere başlangıç saati, sayaç) */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long hourEpoch, AtomicInteger count) {}

    public MobileAuthFilter(
            DeviceTokenService tokenService,
            QuotaService quotaService,
            @Value("${audittrove.security.audit-rate-limit-per-hour:20}") int limitPerHour) {
        this.tokenService = tokenService;
        this.quotaService = quotaService;
        this.limitPerHour = limitPerHour;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!tokenService.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        String method = request.getMethod();
        // Is baslatma: senkron /audit + async /audit/async (tam koruma: token+rate+kota)
        boolean submit = "POST".equalsIgnoreCase(method)
                && ("/api/v1/audit".equals(uri) || "/api/v1/audit/async".equals(uri));
        // Sadece token dogrulanan hafif yollar (rate-limit/kota YOK):
        //  - async durum sorgusu (polling)
        //  - push token kaydi
        boolean statusQuery = "GET".equalsIgnoreCase(method)
                && uri != null && uri.startsWith("/api/v1/audit/jobs/");
        boolean pushToken = "POST".equalsIgnoreCase(method)
                && "/api/v1/devices/push-token".equals(uri);
        return !(submit || statusQuery || pushToken);
    }

    /** Hafif yollar (durum sorgusu + push token kaydi): rate limit ve kota atlanir, yalnizca token dogrulanir. */
    private boolean isTokenOnly(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri == null) return false;
        boolean statusQuery = "GET".equalsIgnoreCase(method) && uri.startsWith("/api/v1/audit/jobs/");
        boolean pushToken = "POST".equalsIgnoreCase(method) && "/api/v1/devices/push-token".equals(uri);
        return statusQuery || pushToken;
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

        // Hafif yollarda (polling / push token kaydi) rate limit ve kota kapisi ATLANIR;
        // sadece sahiplik icin deviceId gerekir.
        if (isTokenOnly(request)) {
            request.setAttribute(DEVICE_ID_ATTR, deviceId.get());
            chain.doFilter(request, response);
            return;
        }

        if (!allow(deviceId.get())) {
            reject(response, 429,
                    "Saatlik inceleme limitine ulaşıldı. Lütfen daha sonra tekrar deneyin.");
            return;
        }

        QuotaService.Decision decision = quotaService.check(deviceId.get());
        if (decision == QuotaService.Decision.MONTHLY_LIMIT_REACHED) {
            response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Aylık ücretsiz inceleme hakkınız doldu.\","
                            + "\"code\":\"MONTHLY_LIMIT_REACHED\","
                            + "\"freeMonthlyLimit\":" + quotaService.freeMonthlyLimit() + "}");
            return;
        }

        request.setAttribute(DEVICE_ID_ATTR, deviceId.get());
        request.setAttribute(QUOTA_DECISION_ATTR, decision);
        chain.doFilter(request, response);
    }

    private boolean allow(String deviceId) {
        long hour = System.currentTimeMillis() / 3_600_000L;
        Window w = windows.compute(deviceId, (k, cur) ->
                cur == null || cur.hourEpoch() != hour
                        ? new Window(hour, new AtomicInteger())
                        : cur);
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