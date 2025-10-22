package com.etikos.demo.service;

import com.etikos.demo.dto.AuditLogRequest;
import com.etikos.demo.entity.AuditAction;
import com.etikos.demo.entity.AuditLog;
import com.etikos.demo.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLog record(AuditLogRequest request) {
        AuditLog log = new AuditLog();
        log.setUid(request.uid());
        log.setActorUid(request.actorUid());
        log.setAction(request.action());
        log.setTimestamp(request.timestamp() != null ? request.timestamp() : Instant.now());
        log.setIp(request.ip());
        log.setUserAgent(request.userAgent());
        log.setMetadata(request.metadata() != null ? new LinkedHashMap<>(request.metadata()) : null);
        return repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String uid,
                                 AuditAction action,
                                 Instant from,
                                 Instant to,
                                 Pageable pageable) {
        Specification<AuditLog> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (uid != null && !uid.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("uid"), uid));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to));
        }

        return repository.findAll(spec, pageable);
    }
}

