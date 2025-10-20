package com.etikos.user.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity // habilita @PreAuthorize, @PostAuthorize, etc.
public class MethodSecurityConfig {}
