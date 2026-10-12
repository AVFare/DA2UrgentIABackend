package com.urgentia.ticket.infrastructure.client;

/**
 * Respuesta 200 de classification-service tal como viene por la red. Todo es texto o
 * wrapper a proposito: la validacion la hace ClasificacionTraductor (ACL), no Jackson.
 * Los campos que no se usan (id, modelo, versionPrompt, latenciaMs) se ignoran.
 */
public record ClasificacionRespuesta(
        String ticketId,
        String categoria,
        String urgencia,
        String impacto,
        String moduloAfectado,
        Boolean requiereEscalamiento,
        Double confianza,
        String justificacion,
        String proveedor,
        String fecha) {
}
