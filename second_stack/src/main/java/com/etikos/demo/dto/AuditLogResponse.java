package com.etikos.demo.dto;

import com.etikos.demo.entity.AuditAction;
import com.etikos.demo.entity.AuditLog;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
        Long id,
        String uid,
        String actorUid,
        AuditAction action,
        Instant timestamp,
        String ip,
        String userAgent,
        Map<String, Object> metadata
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUid(),
                log.getActorUid(),
                log.getAction(),
                log.getTimestamp(),
                log.getIp(),
                log.getUserAgent(),
                log.getMetadata() != null ? Map.copyOf(log.getMetadata()) : Map.of()
        );
    }
}

