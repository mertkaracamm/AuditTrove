package com.audittrove.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PostgresRegulationStore implements RegulationStore {
    private static final String SEARCH_SQL = """
            SELECT provision_key, source_title, law_number, article, paragraph, title, provision_text,
                   authority, document_type, topics::text, applicable_documents::text,
                   effective_from, effective_to, status, official_url, content_hash, verified, keywords::text
              FROM legal_provision
             WHERE status = 'ACTIVE'
               AND (effective_from IS NULL OR effective_from <= CURRENT_DATE)
               AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
               AND search_vector @@ websearch_to_tsquery('simple', ?)
             ORDER BY ts_rank_cd(search_vector, websearch_to_tsquery('simple', ?)) DESC,
                      verified DESC, effective_from DESC NULLS LAST
             LIMIT ?
            """;

    private final ObjectMapper objectMapper;
    private final String url;
    private final String username;
    private final String password;

    public PostgresRegulationStore(ObjectMapper objectMapper,
                                   @Value("${audittrove.rag.database.url:}") String url,
                                   @Value("${audittrove.rag.database.username:}") String username,
                                   @Value("${audittrove.rag.database.password:}") String password,
                                   @Value("${audittrove.rag.database.migrate:true}") boolean migrate) {
        this.objectMapper = objectMapper;
        this.url = url;
        this.username = username;
        this.password = password;
        if (enabled() && migrate) {
            Flyway.configure().dataSource(url, username, password).load().migrate();
        }
    }

    @Override
    public List<RegulationChunk> search(String query, int limit) {
        if (!enabled() || query == null || query.isBlank()) {
            return List.of();
        }
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(SEARCH_SQL)) {
            statement.setString(1, query);
            statement.setString(2, query);
            statement.setInt(3, limit);
            try (ResultSet results = statement.executeQuery()) {
                List<RegulationChunk> matches = new ArrayList<>();
                while (results.next()) {
                    matches.add(map(results));
                }
                return matches;
            }
        } catch (SQLException exception) {
            throw new RegulationStoreException("Mevzuat veritabanında arama yapılamadı", exception);
        }
    }

    private RegulationChunk map(ResultSet row) throws SQLException {
        return new RegulationChunk(
                row.getString("provision_key"), row.getString("source_title"), row.getString("law_number"),
                row.getString("article"), row.getString("paragraph"), row.getString("title"),
                row.getString("provision_text"), row.getString("authority"), row.getString("document_type"),
                strings(row.getString("topics")), strings(row.getString("applicable_documents")),
                date(row, "effective_from"), date(row, "effective_to"), row.getString("status"),
                row.getString("official_url"), row.getString("content_hash"), row.getBoolean("verified"),
                strings(row.getString("keywords")));
    }

    private String[] strings(String json) throws SQLException {
        try {
            return json == null ? new String[0] : objectMapper.readValue(json, String[].class);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Mevzuat metadata alanı okunamadı", exception);
        }
    }

    private LocalDate date(ResultSet row, String column) throws SQLException {
        var value = row.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private boolean enabled() {
        return url != null && !url.isBlank();
    }
}
