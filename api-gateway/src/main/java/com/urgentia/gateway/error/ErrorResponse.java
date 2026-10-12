package com.urgentia.gateway.error;

/** Formato comun de error del proyecto (seccion 5.3 del contexto). */
public record ErrorResponse(
        String codigo,
        String mensaje,
        String timestamp,
        String path,
        String correlationId) {
}
