package com.audittrove.audit;

import com.audittrove.api.AuditResponse;
import com.audittrove.db.PostgresUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * İşlerin veritabanı kaydı. Amaç: sunucu yeniden başladığında telefon işini kaybetmesin; hangi kopya
 * cevap verirse versin durum ortak yerden okunsun. Adres verilmemişse sessizce kapalı çalışır (yalnız bellek).
 *
 * Gizlilik: belge ve sayfa metinleri buraya YAZILMAZ. Yalnızca raporun kendisi, kısa süreli (TTL) tutulur.
 */
@Repository
public class AuditJobRecords {
    private static final Logger log = LoggerFactory.getLogger(AuditJobRecords.class);

    private static final String CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS audit_jobs (
                id         VARCHAR(64) PRIMARY KEY,
                device_id  VARCHAR(64),
                file_name  VARCHAR(512),
                language   VARCHAR(16),
                status     VARCHAR(16)  NOT NULL,
                result     TEXT,
                error      TEXT,
                created_at BIGINT       NOT NULL,
                updated_at BIGINT       NOT NULL
            )
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO audit_jobs (id, device_id, file_name, language, status, result, error, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, result = EXCLUDED.result,
                error = EXCLUDED.error, updated_at = EXCLUDED.updated_at
            """;

    private static final String SELECT_SQL =
            "SELECT device_id, file_name, language, status, result, error, created_at, updated_at FROM audit_jobs WHERE id = ?";

    private static final String DELETE_OLD_SQL = "DELETE FROM audit_jobs WHERE created_at < ?";

    private final String url;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;

    public AuditJobRecords(ObjectMapper objectMapper,
                           @Value("${audittrove.jobs.database-url:${audittrove.security.usage-database-url:}}") String rawUrl) {
        this.objectMapper = objectMapper;
        PostgresUrl parsed = PostgresUrl.parse(rawUrl);
        if (parsed == null) {
            this.url = null;
            this.username = null;
            this.password = null;
            log.info("İş kaydı veritabanı adresi yok; işler yalnız bellekte tutulacak.");
            return;
        }
        this.url = parsed.url();
        this.username = parsed.username();
        this.password = parsed.password();
        initSchema();
    }

    public boolean isEnabled() {
        return url != null;
    }

    /** İşin güncel hâlini yazar (ilk kayıt ya da durum değişimi). Hata olursa iş düşmez, yalnız loglanır. */
    public void save(AuditJob job) {
        if (!isEnabled() || job == null) return;
        try (Connection c = connect();
             PreparedStatement s = c.prepareStatement(UPSERT_SQL)) {
            s.setString(1, job.id());
            s.setString(2, job.deviceId());
            s.setString(3, job.fileName());
            s.setString(4, job.language());
            s.setString(5, job.status().name());
            s.setString(6, serialize(job.result()));
            s.setString(7, job.error());
            s.setLong(8, job.createdAt());
            s.setLong(9, System.currentTimeMillis());
            s.executeUpdate();
        } catch (SQLException e) {
            log.warn("Is kaydi yazilamadi (job {}): {}", job.id(), e.getMessage());
        }
    }

    /** Bellekte olmayan iş: kayıttan geri kurulur. Bulunamazsa null. */
    public AuditJob find(String id) {
        if (!isEnabled() || id == null) return null;
        try (Connection c = connect();
             PreparedStatement s = c.prepareStatement(SELECT_SQL)) {
            s.setString(1, id);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return null;
                AuditJob job = AuditJob.restored(id, rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(7));
                job.setStatus(AuditJob.Status.valueOf(rs.getString(4)));
                job.setResult(deserialize(rs.getString(5)));
                job.setError(rs.getString(6));
                job.setUpdatedAt(rs.getLong(8));
                return job;
            }
        } catch (Exception e) {
            log.warn("Is kaydi okunamadi (job {}): {}", id, e.getMessage());
            return null;
        }
    }

    /** Süresi dolan kayıtları siler; rapor sunucuda kalıcı durmaz. */
    public void deleteOlderThan(long createdBefore) {
        if (!isEnabled()) return;
        try (Connection c = connect();
             PreparedStatement s = c.prepareStatement(DELETE_OLD_SQL)) {
            s.setLong(1, createdBefore);
            int n = s.executeUpdate();
            if (n > 0) log.info("Suresi dolan {} is kaydi silindi", n);
        } catch (SQLException e) {
            log.warn("Eski is kayitlari silinemedi: {}", e.getMessage());
        }
    }

    private String serialize(AuditResponse response) {
        if (response == null) return null;
        try {
            // Sayfa metinleri cihazda kalır; veritabanına belge içeriği yazılmaz.
            return objectMapper.writeValueAsString(response.withoutPageTexts());
        } catch (Exception e) {
            log.warn("Rapor kayda yazilamadi: {}", e.getMessage());
            return null;
        }
    }

    private AuditResponse deserialize(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, AuditResponse.class);
        } catch (Exception e) {
            log.warn("Kayittaki rapor okunamadi: {}", e.getMessage());
            return null;
        }
    }

    private void initSchema() {
        try (Connection c = connect();
             PreparedStatement s = c.prepareStatement(CREATE_SQL)) {
            s.executeUpdate();
        } catch (SQLException e) {
            log.error("audit_jobs tablosu olusturulamadi: {}", e.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        return username != null
                ? DriverManager.getConnection(url, username, password)
                : DriverManager.getConnection(url);
    }
}
