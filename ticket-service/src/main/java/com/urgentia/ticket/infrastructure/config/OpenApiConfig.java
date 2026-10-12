package com.urgentia.ticket.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Datos del Swagger de ticket-service. Declara el esquema "bearer" para que el
 * Swagger unificado del gateway muestre el boton Authorize (seccion 18 del contexto).
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_SEGURIDAD = "bearer";

    @Bean
    public OpenAPI openApi(@Value("${servicio.version}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("UrgentIA - ticket-service")
                        .description("Core Domain de UrgentIA: tickets, prioridad (P1-P4), SLA, estados y "
                                + "escalamiento. La IA sugiere la clasificacion; el dominio decide la prioridad.")
                        .version(version))
                .components(new Components().addSecuritySchemes(ESQUEMA_SEGURIDAD, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token emitido por POST /api/auth/login. Lo valida el gateway.")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SEGURIDAD));
    }
}
