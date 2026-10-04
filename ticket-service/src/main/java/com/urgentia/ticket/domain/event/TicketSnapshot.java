package com.urgentia.ticket.domain.event;

import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.ModuloAfectado;
import com.urgentia.ticket.domain.model.Prioridad;
import java.time.Instant;
import java.util.UUID;

/**
 * Foto del ticket despues del cambio que genero el evento (seccion 10.1).
 * No incluye la descripcion a proposito: puede tener datos sensibles.
 * prioridad, categoria, moduloAfectado, agenteAsignadoId y fechaLimiteSla pueden ser null.
 */
public record TicketSnapshot(
        UUID ticketId,
        String titulo,
        EstadoTicket estado,
        Prioridad prioridad,
        Categoria categoria,
        ModuloAfectado moduloAfectado,
        UUID solicitanteId,
        UUID agenteAsignadoId,
        Instant fechaCreacion,
        Instant fechaLimiteSla) {
}
