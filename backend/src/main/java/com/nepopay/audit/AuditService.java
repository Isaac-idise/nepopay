package com.nepopay.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records important actions. RULE: never put passwords, PINs, tokens or other secrets in metadata. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public void record(UUID userId, String action, String entityType, String entityId, Map<String, Object> metadata) {
        try {
            String json = metadata == null ? null : mapper.writeValueAsString(metadata);
            repository.save(new AuditLog(userId, action, entityType, entityId, json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise audit metadata", e);
        }
    }
}
