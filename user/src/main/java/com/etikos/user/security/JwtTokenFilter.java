package com.etikos.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtTokenFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                if (jwtService.validateToken(token)) {
                    String username = jwtService.extractUsername(token);
                    String role = jwtService.extractRole(token);
                    String userId = jwtService.extractUserId(token);

                    // Crear authorities con el rol
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

                    // Crear authentication con userId como principal
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId, // principal es el userId (String)
                                    null,
                                    Collections.singletonList(authority)
                            );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token inválido, continuar sin autenticación
                logger.error("Error validating JWT token: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
