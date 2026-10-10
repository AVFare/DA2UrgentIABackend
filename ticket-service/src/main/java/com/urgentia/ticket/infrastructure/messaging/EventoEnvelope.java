package com.urgentia.ticket.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.domain.event.TicketEscalado;
import com.urgentia.ticket.domain.event.TicketEstadoCambiado;
import com.urgentia.ticket.domain.event.TicketResuelto;
import com.urgentia.ticket.domain.event.TicketSnapshot;
import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.ModuloAfectado;
import com.urgentia.ticket.domain.model.Prioridad;
import java.time.Instant;
import java.util.UUID;

/**
 * Sobre estandar de los eventos (seccion 10.1). Es el mismo en la parte 2, cuando viaje
 * por un broker. Esquema formal: contracts/events/ticket-events.schema.json.
 */
public record EventoEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String source,
        Payload payload) {

    public static final int VERSION = 1;
    public static final String SOURCE = "ticket-service";

    public static EventoEnvelope desde(DomainEvent evento, String correlationId) {
        EstadoTicket estadoAnterior = null;
        String motivo = null;
        if (evento instanceof TicketEstadoCambiado cambio) {
            estadoAnterior = cambio.estadoAnterior();
        } else if (evento instanceof TicketEscalado escalado) {
            estadoAnterior = escalado.estadoAnterior();
            motivo = escalado.motivo();
        } else if (evento instanceof TicketResuelto resuelto) {
            estadoAnterior = resuelto.estadoAnterior();
        }
        return new EventoEnvelope(evento.eventId(), evento.tipo(), VERSION, evento.occurredAt(), correlationId,
                SOURCE, new Payload(TicketPayload.desde(evento.ticket()), estadoAnterior, motivo));
    }

    /** estadoAnterior y motivo se omiten cuando el tipo de evento no los lleva. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Payload(TicketPayload ticket, EstadoTicket estadoAnterior, String motivo) {
    }

    /** Foto del ticket. Sin descripcion (puede tener datos sensibles). Los null se envian como null. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TicketPayload(
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

        static TicketPayload desde(TicketSnapshot s) {
            return new TicketPayload(s.ticketId(), s.titulo(), s.estado(), s.prioridad(), s.categoria(),
                    s.moduloAfectado(), s.solicitanteId(), s.agenteAsignadoId(), s.fechaCreacion(),
                    s.fechaLimiteSla());
        }
    }
}
