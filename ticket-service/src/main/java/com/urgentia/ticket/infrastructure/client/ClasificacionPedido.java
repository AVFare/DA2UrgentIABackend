package com.urgentia.ticket.infrastructure.client;

import java.util.UUID;

/** Body de POST /api/clasificaciones (contrato de classification-service, seccion 9.3). */
public record ClasificacionPedido(UUID ticketId, String titulo, String descripcion) {
}
