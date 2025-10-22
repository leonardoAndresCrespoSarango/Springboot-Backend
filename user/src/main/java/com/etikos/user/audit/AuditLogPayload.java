package com.etikos.user.audit;

import java.time.Instant;
import java.util.Map;

public record AuditLogPayload(
        String uid,
        String actorUid,
        AuditAction action,
        Instant timestamp,
        String ip,
        String userAgent,
        Map<String, Object> metadata
) {
}

