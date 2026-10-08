package com.urgentia.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Datos del Swagger de user-service. Declara el esquema "bearer" para que el
 * Swagger unificado del gateway muestre el boton Authorize.
 */
@Configuration
public class OpenApiConfig {

    public static final String ESQUEMA_SEGURIDAD = "bearer";

    @Bean
    public OpenAPI openApi(@Value("${servicio.version}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("UrgentIA - user-service")
                        .description("Identidad y acceso de UrgentIA: alta de usuarios, listado y login con JWT. "
                                + "Este servicio emite el JWT que valida el gateway.")
                        .version(version))
                .components(new Components().addSecuritySchemes(ESQUEMA_SEGURIDAD, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token de POST /api/auth/login (lo emite este servicio). Lo valida el gateway.")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SEGURIDAD));
    }
}
