package com.urgentia.ticket.domain.event;

import com.urgentia.ticket.domain.model.EstadoTicket;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** El ticket paso a ESCALADO (automatico por P1 o manual). Lleva el estado anterior y el motivo. */
public record TicketEscalado(
        UUID eventId, Instant occurredAt, TicketSnapshot ticket, EstadoTicket estadoAnterior, String motivo)
        implements DomainEvent {

    public static final String TIPO = "TicketEscalado";

    public TicketEscalado {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(estadoAnterior, "estadoAnterior");
        Objects.requireNonNull(motivo, "motivo");
    }

    public TicketEscalado(Instant occurredAt, TicketSnapshot ticket, EstadoTicket estadoAnterior, String motivo) {
        this(UUID.randomUUID(), occurredAt, ticket, estadoAnterior, motivo);
    }

    @Override
    public String tipo() {
        return TIPO;
    }
}
