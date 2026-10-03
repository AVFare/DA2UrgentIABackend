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
                .build();
    }
}