package com.etikos.demo.controller;

import com.etikos.demo.dto.AuditLogRequest;
import com.etikos.demo.dto.AuditLogResponse;
import com.etikos.demo.entity.AuditAction;
import com.etikos.demo.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/audits")
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditLogResponse recordAudit(@Valid @RequestBody AuditLogRequest request) {
        return AuditLogResponse.from(auditLogService.record(request));
    }

    @GetMapping
    public Page<AuditLogResponse> searchAudits(@RequestParam(required = false) String uid,
                                               @RequestParam(required = false) AuditAction action,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                               @PageableDefault(sort = "timestamp", direction = Sort.Direction.DESC)
                                               Pageable pageable) {
        return auditLogService.search(uid, action, from, to, pageable)
                .map(AuditLogResponse::from);
    }
}

