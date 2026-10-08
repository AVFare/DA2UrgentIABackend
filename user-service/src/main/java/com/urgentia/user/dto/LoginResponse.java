package com.urgentia.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "3600") long expiresIn,
        UsuarioLogin usuario) {

    /** Datos minimos del usuario autenticado (seccion 9.1 del contexto): sin activo ni fechaAlta. */
    public record UsuarioLogin(
            @Schema(example = "b1c2d3e4-0000-4000-8000-000000000004") String id,
            @Schema(example = "Sofía Solicitante") String nombre,
            @Schema(example = "solicitante@urgentia.local") String email,
            @Schema(example = "SOLICITANTE") String rol) {
    }
}
