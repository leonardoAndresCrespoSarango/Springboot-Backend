package com.etikos.demo.controller;

import com.etikos.demo.dto.AuditLogRequest;
import com.etikos.demo.dto.AuditLogResponse;
import com.etikos.demo.entity.AuditAction;
import com.etikos.demo.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/audits")
@CrossOrigin(origins = "*")
@Tag(name = "Auditoria", description = "Registro y consulta de eventos de auditoria")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Operation(
            summary = "Registrar evento de auditoria",
            description = "Crea un registro de auditoria con la informacion enviada por otros microservicios."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evento registrado", content = @Content(schema = @Schema(implementation = AuditLogResponse.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditLogResponse recordAudit(@Valid @RequestBody AuditLogRequest request) {
        return AuditLogResponse.from(auditLogService.record(request));
    }

    @Operation(
            summary = "Buscar eventos de auditoria",
            description = "Filtra los eventos por usuario, accion y rango de fechas. Soporta parametros de paginacion de Spring."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultados paginados"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @GetMapping
    public Page<AuditLogResponse> searchAudits(
            @Parameter(description = "UID del usuario afectado") @RequestParam(required = false) String uid,
            @Parameter(description = "Accion auditada") @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Fecha-hora inicial (ISO-8601)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Fecha-hora final (ISO-8601)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Parametros de paginacion (page, size, sort)") @PageableDefault(sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return auditLogService.search(uid, action, from, to, pageable)
                .map(AuditLogResponse::from);
    }
}

