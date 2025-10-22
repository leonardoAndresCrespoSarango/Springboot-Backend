package com.etikos.user.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "audit.service")
public class AuditServiceProperties {

    /**
     * URL base del microservicio de auditoría (por ejemplo http://localhost:8081).
     */
    private URI baseUrl = URI.create("http://localhost:8081");

    /**
     * Timeout de conexión para las peticiones HTTP.
     */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /**
     * Timeout de lectura para las peticiones HTTP.
     */
    private Duration readTimeout = Duration.ofSeconds(5);

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}

