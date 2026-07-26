package com.game.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;

@Repository
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed collaborators are intentionally injected and not exposed")
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(UUID actorUserId, String action, String targetType, UUID targetId,
                       String ipAddress, Map<String, Object> metadata) {
        jdbcTemplate.update("""
                insert into audit_logs (actor_user_id, action, target_type, target_id, ip_address, metadata)
                values (?, ?, ?, ?, ?::inet, ?::jsonb)
                """, actorUserId, action, targetType, targetId, ipAddress, toJson(metadata));
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid audit metadata", exception);
        }
    }
}
