package com.etikos.user.audit;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public void log(String uid, String actorUid, AuditAction action,
                    HttpServletRequest req, Map<String, Object> meta) throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        Map<String, Object> doc = new HashMap<>();
        doc.put("uid", uid);
        doc.put("actorUid", actorUid);
        doc.put("action", action.name());
        doc.put("timestamp", com.google.cloud.Timestamp.now());
        doc.put("ip", extractIp(req));
        doc.put("userAgent", req != null ? req.getHeader("User-Agent") : null);
        if (meta != null) doc.put("meta", meta);

        db.collection("audit_logs").document(UUID.randomUUID().toString())
                .set(doc).get();

        // Log a consola para mejor visibilidad
        if (action == AuditAction.LOGIN_FAILED) {
            String reason = meta != null ? (String) meta.get("reason") : "UNKNOWN";
            String email = meta != null ? (String) meta.get("email") : "unknown";
            log.warn("🔴 LOGIN FAILED - Email: {} | Reason: {} | IP: {}", email, reason, extractIp(req));
        } else if (action == AuditAction.LOGIN) {
            log.info("✅ LOGIN SUCCESS - User: {} | IP: {}", uid, extractIp(req));
        } else if (action == AuditAction.REGISTER) {
            log.info("📝 USER REGISTERED - User: {} | IP: {}", uid, extractIp(req));
        } else if (action == AuditAction.LOGOUT) {
            log.info("👋 LOGOUT - User: {} | IP: {}", uid, extractIp(req));
        } else {
            log.info("📊 AUDIT LOG - Action: {} | User: {} | Actor: {} | IP: {}",
                    action.name(), uid, actorUid, extractIp(req));
        }
    }

    private String extractIp(HttpServletRequest req) {
        if (req == null) return null;
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
