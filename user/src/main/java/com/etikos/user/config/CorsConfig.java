package com.etikos.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permitir orígenes (Angular en desarrollo y producción)
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",        // Angular desarrollo
                "http://localhost:4201",        // Angular desarrollo (puerto alternativo)
                "http://localhost:3000",        // Otro puerto común
                "https://tu-dominio.com"        // Producción (cambiar por tu dominio)
        ));

        // Permitir todos los métodos HTTP
        configuration.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS",
                "PATCH"
        ));

        // Permitir todos los headers
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Permitir credenciales (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // Exponer headers adicionales al cliente
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type"
        ));

        // Tiempo de cache para las peticiones preflight (OPTIONS)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

