package com.audittrove.security;

import com.audittrove.db.PostgresUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;

// Aylik kullanim sayaclari. URL verilmemisse kota kapali calisir.
@Repository
public class DeviceUsageStore {

    private static final Logger log = LoggerFactory.getLogger(DeviceUsageStore.class);

    private static final String CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS device_usage (
                device_id  VARCHAR(64)  NOT NULL,
                month      CHAR(7)      NOT NULL,
                used_count INTEGER      NOT NULL DEFAULT 0,
                PRIMARY KEY (device_id, month)
            )
            """;

    private static final String COUNT_SQL =
            "SELECT used_count FROM device_usage WHERE device_id = ? AND month = ?";

    private static final String INCREMENT_SQL = """
            INSERT INTO device_usage (device_id, month, used_count) VALUES (?, ?, 1)
            ON CONFLICT (device_id, month) DO UPDATE SET used_count = device_usage.used_count + 1
            """;

    private final String url;
    private final String username;
    private final String password;

    public DeviceUsageStore(@Value("${audittrove.security.usage-database-url:}") String rawUrl) {
        PostgresUrl parsed = PostgresUrl.parse(rawUrl);
        if (parsed == null) {
            this.url = null;
            this.username = null;
            this.password = null;
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

    public int currentMonthUsage(String deviceId) {
        if (!isEnabled()) return 0;
        try (Connection c = connect();
             PreparedStatement s = c.prepareStatement(COUNT_SQL)) {
            s.setString(1, deviceId);
            s.setString(2, currentMonth());
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            log.warn("Kullanim sayaci okunamadi: {}", e.getMessage());
            return 0;
        }
    }

    public void increment(String deviceId) {
        if (!isEnabled()) return;
        try (Connection c = connect();
             PreparedStatement s = c.prepareStatement(INCREMENT_SQL)) {
            s.setString(1, deviceId);
            s.setString(2, currentMonth());
            s.executeUpdate();
        } catch (SQLException e) {
            log.warn("Kullanim sayaci artirilamadi: {}", e.getMessage());
        }
    }

    private void initSchema() {
        try (Connection c = connect();
             PreparedStatement s = c.prepareStatement(CREATE_SQL)) {
            s.executeUpdate();
        } catch (SQLException e) {
            log.error("device_usage tablosu olusturulamadi: {}", e.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        return username != null
                ? DriverManager.getConnection(url, username, password)
                : DriverManager.getConnection(url);
    }

    private static String currentMonth() {
        return YearMonth.now().toString();
    }
}