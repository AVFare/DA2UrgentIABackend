package com.urgentia.ticket.domain.event;

import com.urgentia.ticket.domain.model.EstadoTicket;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** El ticket paso a RESUELTO. Lleva el estado anterior. */
public record TicketResuelto(UUID eventId, Instant occurredAt, TicketSnapshot ticket, EstadoTicket estadoAnterior)
        implements DomainEvent {

    public static final String TIPO = "TicketResuelto";

    public TicketResuelto {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(estadoAnterior, "estadoAnterior");
    }

    public TicketResuelto(Instant occurredAt, TicketSnapshot ticket, EstadoTicket estadoAnterior) {
        this(UUID.randomUUID(), occurredAt, ticket, estadoAnterior);
    }

    @Override
    public String tipo() {
        return TIPO;
    }
}
