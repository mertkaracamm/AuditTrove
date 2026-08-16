package com.audittrove.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// pro -> sinirsiz, degilse aylik ucretsiz limit
@Service
public class QuotaService {

    public enum Decision { ALLOWED, ALLOWED_PRO, MONTHLY_LIMIT_REACHED }

    private final RevenueCatClient revenueCat;
    private final DeviceUsageStore usageStore;
    private final int freeMonthlyLimit;

    public QuotaService(RevenueCatClient revenueCat,
                        DeviceUsageStore usageStore,
                        @Value("${audittrove.security.free-monthly-limit:5}") int freeMonthlyLimit) {
        this.revenueCat = revenueCat;
        this.usageStore = usageStore;
        this.freeMonthlyLimit = freeMonthlyLimit;
    }

    public boolean isEnabled() {
        return usageStore.isEnabled();
    }

    public Decision check(String deviceId) {
        if (!isEnabled()) {
            return Decision.ALLOWED;
        }
        if (revenueCat.isPro(deviceId)) {
            return Decision.ALLOWED_PRO;
        }
        return usageStore.currentMonthUsage(deviceId) < freeMonthlyLimit
                ? Decision.ALLOWED
                : Decision.MONTHLY_LIMIT_REACHED;
    }

    public void recordUsage(String deviceId, Decision decision) {
        if (isEnabled() && decision == Decision.ALLOWED) {
            usageStore.increment(deviceId);
        }
    }

    public int freeMonthlyLimit() {
        return freeMonthlyLimit;
    }
}