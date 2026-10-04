package com.audittrove.db;

import java.net.URI;

/**
 * Railway'in verdiği "postgresql://kullanıcı:parola@host:port/veritabanı" adresini JDBC biçimine çevirir.
 * Adres zaten jdbc: ile başlıyorsa olduğu gibi kullanılır; boş ise ilgili özellik kapalı çalışır.
 */
public record PostgresUrl(String url, String username, String password) {

    /** Boş ya da tanınmayan adres için null döner; çağıran "kapalı" davranışını seçer. */
    public static PostgresUrl parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.startsWith("postgresql://") || value.startsWith("postgres://")) {
            URI uri = URI.create(value.replaceFirst("^postgres(ql)?", "postgresql"));
            String[] userInfo = uri.getUserInfo() != null ? uri.getUserInfo().split(":", 2) : new String[0];
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
            return new PostgresUrl(
                    "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath() + query,
                    userInfo.length > 0 ? userInfo[0] : null,
                    userInfo.length > 1 ? userInfo[1] : null);
        }
        return new PostgresUrl(value, null, null);
    }
}
