package com.urgentia.ticket.infrastructure.rest.dto;

import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Prioridad;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "Ticket", description = "Ticket con su prioridad, SLA y la clasificacion de la IA. "
        + "Si esta PENDIENTE_CLASIFICACION: clasificacion, prioridad, fechaLimiteSla y motivoEscalamiento son null.")
public record TicketResponse(
        @Schema(example = "7c9e6679-7425-40de-944b-e07fc1f90ae7") UUID id,
        @Schema(example = "No puede ingresar nadie") String titulo,
        @Schema(example = "Producción caída, todos los usuarios bloqueados en el login desde las 9") String descripcion,
        @Schema(example = "b1c2d3e4-0000-4000-8000-000000000004") UUID solicitanteId,
        @Schema(nullable = true) UUID agenteAsignadoId,
        @Schema(example = "ESCALADO") EstadoTicket estado,
        @Schema(example = "P1", nullable = true) Prioridad prioridad,
        @Schema(example = "2026-10-05T15:03:11Z", nullable = true) Instant fechaLimiteSla,
        @Schema(example = "false") boolean requiereRevisionManual,
        @Schema(example = "Prioridad P1", nullable = true) String motivoEscalamiento,
        @Schema(nullable = true) ClasificacionResponse clasificacion,
        @Schema(example = "2026-10-05T14:03:10Z") Instant fechaCreacion,
        @Schema(example = "2026-10-05T14:03:11Z") Instant fechaActualizacion) {
}
