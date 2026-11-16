package com.urgentia.ticket.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje ClasificacionSolicitada (seccion 10.5, contracts/events/clasificacion-events.schema.json):
 * el mismo sobre que los eventos de dominio, con el pedido de clasificacion como payload.
 * Lleva la descripcion, por eso viaja solo por el exchange privado urgentia.clasificacion.
 */
public record SobreClasificacionSolicitada(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String source,
        Payload payload) {

    /** Igual que el cuerpo de POST /api/clasificaciones. */
    public record Payload(UUID ticketId, String titulo, String descripcion) {
    }

    public static SobreClasificacionSolicitada nuevo(UUID ticketId, String titulo, String descripcion,
                                                     Instant ahora, String correlationId) {
        return new SobreClasificacionSolicitada(UUID.randomUUID(), TopologiaRabbit.CLASIFICACION_SOLICITADA,
                EventoEnvelope.VERSION, ahora, correlationId, EventoEnvelope.SOURCE,
                new Payload(ticketId, titulo, descripcion));
    }
}
