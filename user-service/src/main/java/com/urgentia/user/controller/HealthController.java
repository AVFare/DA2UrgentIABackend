package com.urgentia.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /health con el formato comun a todos los servicios (seccion 5 del contexto). */
@RestController
@Tag(name = "Health")
public class HealthController {

    private final Health health;

    public HealthController(@Value("${spring.application.name}") String servicio,
                            @Value("${servicio.version}") String version) {
        this.health = new Health("UP", servicio, version);
    }

    @GetMapping("/health")
    @Operation(summary = "Estado del servicio")
    @SecurityRequirements
    public Health health() {
        return health;
    }

    public record Health(String status, String service, String version) {
    }
}
