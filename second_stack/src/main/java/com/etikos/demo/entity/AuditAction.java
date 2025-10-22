package com.etikos.demo.entity;

/**
 * Enumeración de acciones auditadas para mantener
 * compatibilidad con el microservicio de usuarios.
 */
public enum AuditAction {
    REGISTER,
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    PASSWORD_RESET_LINK_SENT,
    CREDENTIALS_UPDATED,
    USER_BLOCKED,
    USER_UNBLOCKED,
    ROLE_CHANGED
}

