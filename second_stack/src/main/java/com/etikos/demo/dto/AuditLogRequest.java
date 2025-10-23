package com.etikos.demo.dto;

import com.etikos.demo.entity.AuditAction;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogRequest(
        String uid,
        String actorUid,
        @NotNull AuditAction action,
        Instant timestamp,
        String ip,
        String userAgent,
        Map<String, Object> metadata
) {
}

