package com.etikos.user.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final RestTemplate restTemplate;
    private final URI auditEndpoint;

    public AuditService(RestTemplateBuilder restTemplateBuilder, AuditServiceProperties properties) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(properties.getConnectTimeout())
                .setReadTimeout(properties.getReadTimeout())
                .build();
        this.auditEndpoint = properties.getBaseUrl().resolve("/api/audits");
    }

    public void log(String uid, String actorUid, AuditAction action,
                    HttpServletRequest req, Map<String, Object> meta) throws AuditClientException {
        Map<String, Object> metadata = meta != null ? new HashMap<>(meta) : null;
        AuditLogPayload payload = new AuditLogPayload(
                uid,
                actorUid,
                action,
                Instant.now(),
                extractIp(req),
                req != null ? req.getHeader("User-Agent") : null,
                metadata
        );

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(auditEndpoint, payload, Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AuditClientException("El servicio de auditoría respondió con estado " + response.getStatusCode(), null);
            }
        } catch (RestClientException e) {
            log.error("Fallo al enviar evento de auditoría al servicio secundario {}", auditEndpoint, e);
            throw new AuditClientException("No se pudo registrar el evento de auditoría en el servicio secundario", e);
        }

        logConsole(uid, actorUid, action, req, metadata);
    }

    private void logConsole(String uid, String actorUid, AuditAction action,
                            HttpServletRequest req, Map<String, Object> meta) {
        String ip = extractIp(req);
        if (action == AuditAction.LOGIN_FAILED) {
            String reason = meta != null ? (String) meta.get("reason") : "UNKNOWN";
            String email = meta != null ? (String) meta.get("email") : "unknown";
            log.warn("🔴 LOGIN FAILED - Email: {} | Reason: {} | IP: {}", email, reason, ip);
        } else if (action == AuditAction.LOGIN) {
            log.info("✅ LOGIN SUCCESS - User: {} | IP: {}", uid, ip);
        } else if (action == AuditAction.REGISTER) {
            log.info("📝 USER REGISTERED - User: {} | IP: {}", uid, ip);
        } else if (action == AuditAction.LOGOUT) {
            log.info("👋 LOGOUT - User: {} | IP: {}", uid, ip);
        } else {
            log.info("📊 AUDIT LOG - Action: {} | User: {} | Actor: {} | IP: {}",
                    action.name(), uid, actorUid, ip);
        }
    }

    private String extractIp(HttpServletRequest req) {
        if (req == null) return null;
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
