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
    /** Rapora soru sor: ayrı saatlik pencere, aylık kotaya girmez. */
    private final Map<String, Window> chatWindows = new ConcurrentHashMap<>();
    private final Map<String, Window> diffWindows = new ConcurrentHashMap<>();
    private final int chatLimitPerHour;
    private final int diffLimitPerHour;

    private record Window(long hourEpoch, AtomicInteger count) {}

    public MobileAuthFilter(
            DeviceTokenService tokenService,
            QuotaService quotaService,
            @Value("${audittrove.security.audit-rate-limit-per-hour:20}") int limitPerHour,
            @Value("${audittrove.security.chat-rate-limit-per-hour:40}") int chatLimitPerHour,
            @Value("${audittrove.security.diff-rate-limit-per-hour:10}") int diffLimitPerHour) {
        this.tokenService = tokenService;
        this.quotaService = quotaService;
        this.limitPerHour = limitPerHour;
        this.chatLimitPerHour = chatLimitPerHour;
        this.diffLimitPerHour = diffLimitPerHour;
    }

    // Soru-cevap ve karşılaştırma: aylık inceleme kotasına girmez, kendi saatlik pencereleri var.
    private static boolean isChat(String method, String uri) {
        return "POST".equalsIgnoreCase(method) && "/api/v1/audit/chat".equals(uri);
    }

    private static boolean isDiff(String method, String uri) {
        return "POST".equalsIgnoreCase(method) && "/api/v1/audit/diff".equals(uri);
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
        boolean cancel = "POST".equalsIgnoreCase(method)
                && uri != null && uri.startsWith("/api/v1/audit/jobs/") && uri.endsWith("/cancel");
        return !(submit || statusQuery || pushToken || cancel || isChat(method, uri) || isDiff(method, uri));
    }

    /** Hafif yollar (durum sorgusu + push token kaydi): rate limit ve kota atlanir, yalnizca token dogrulanir. */
    private boolean isTokenOnly(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri == null) return false;
        boolean statusQuery = "GET".equalsIgnoreCase(method) && uri.startsWith("/api/v1/audit/jobs/");
        boolean pushToken = "POST".equalsIgnoreCase(method) && "/api/v1/devices/push-token".equals(uri);
        boolean cancel = "POST".equalsIgnoreCase(method)
                && uri.startsWith("/api/v1/audit/jobs/") && uri.endsWith("/cancel");
        return statusQuery || pushToken || cancel;
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

        // Soru-cevap: inceleme kotasına dokunmaz, yalnızca kendi saatlik sınırı var.
        if (isChat(request.getMethod(), request.getRequestURI())) {
            if (!allow(chatWindows, deviceId.get(), chatLimitPerHour)) {
                reject(response, 429, "Saatlik soru limitine ulaşıldı. Lütfen daha sonra tekrar deneyin.");
                return;
            }
            request.setAttribute(DEVICE_ID_ATTR, deviceId.get());
            chain.doFilter(request, response);
            return;
        }
        if (isDiff(request.getMethod(), request.getRequestURI())) {
            if (!allow(diffWindows, deviceId.get(), diffLimitPerHour)) {
                reject(response, 429, "Saatlik karşılaştırma limitine ulaşıldı. Lütfen daha sonra tekrar deneyin.");
                return;
            }
            request.setAttribute(DEVICE_ID_ATTR, deviceId.get());
            chain.doFilter(request, response);
            return;
        }

        if (!allow(windows, deviceId.get(), limitPerHour)) {
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

    private static boolean allow(Map<String, Window> windows, String deviceId, int limit) {
        long hour = System.currentTimeMillis() / 3_600_000L;
        Window w = windows.compute(deviceId, (k, cur) ->
                cur == null || cur.hourEpoch() != hour
                        ? new Window(hour, new AtomicInteger())
                        : cur);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().hourEpoch() != hour);
        }
        return w.count().incrementAndGet() <= limit;
    }

    private void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}