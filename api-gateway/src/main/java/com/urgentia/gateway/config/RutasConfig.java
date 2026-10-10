package com.urgentia.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RutasConfig {

    @Bean
    public RouteLocator rutas(
            RouteLocatorBuilder builder,
            @Value("${servicios.user}") String userService,
            @Value("${servicios.ticket}") String ticketService,
            @Value("${servicios.classification}") String classificationService,
            @Value("${servicios.notification}") String notificationService,
            @Value("${servicios.reporting}") String reportingService) {
        // /api/eventos no tiene ruta a proposito: solo se usa por la red interna.
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/auth/**", "/api/usuarios", "/api/usuarios/**")
                        .uri(userService))
                .route("ticket-service", r -> r
                        .path("/api/tickets", "/api/tickets/**")
                        .uri(ticketService))
                .route("classification-service", r -> r
                        .path("/api/clasificaciones", "/api/clasificaciones/**")
                        .uri(classificationService))
                .route("notification-service", r -> r
                        .path("/api/notificaciones", "/api/notificaciones/**")
                        .uri(notificationService))
                .route("reporting-service", r -> r
                        .path("/api/reportes", "/api/reportes/**")
                        .uri(reportingService))
                // Documentacion OpenAPI de cada servicio, para el Swagger unificado.
                // Cada tecnologia la publica en una ruta distinta (seccion 5.2 del contexto).
                .route("docs-user-service", r -> r
                        .path("/v3/api-docs/user-service")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(userService))
                .route("docs-ticket-service", r -> r
                        .path("/v3/api-docs/ticket-service")
                        .filters(f -> f.setPath("/v3/api-docs"))
                        .uri(ticketService))
                .route("docs-classification-service", r -> r
                        .path("/v3/api-docs/classification-service")
                        .filters(f -> f.setPath("/openapi.json"))
                        .uri(classificationService))
                .route("docs-notification-service", r -> r
                        .path("/v3/api-docs/notification-service")
                        .filters(f -> f.setPath("/openapi.json"))
                        .uri(notificationService))
                .route("docs-reporting-service", r -> r
                        .path("/v3/api-docs/reporting-service")
                        .filters(f -> f.setPath("/api-docs-json"))
                        .uri(reportingService))
                .build();
    }
}