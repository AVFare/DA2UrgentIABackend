package com.urgentia.ticket.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Se asigno (o reasigno) un agente al ticket. */
public record TicketAsignado(UUID eventId, Instant occurredAt, TicketSnapshot ticket) implements DomainEvent {

    public static final String TIPO = "TicketAsignado";

    public TicketAsignado {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(ticket, "ticket");
    }

    public TicketAsignado(Instant occurredAt, TicketSnapshot ticket) {
        this(UUID.randomUUID(), occurredAt, ticket);
    }

    @Override
    public String tipo() {
        return TIPO;
    }
}
