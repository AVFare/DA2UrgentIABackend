package com.urgentia.ticket.domain.event;

import com.urgentia.ticket.domain.model.EstadoTicket;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Cambio el estado del ticket. Lleva el estado anterior. */
public record TicketEstadoCambiado(UUID eventId, Instant occurredAt, TicketSnapshot ticket, EstadoTicket estadoAnterior)
        implements DomainEvent {

    public static final String TIPO = "TicketEstadoCambiado";

    public TicketEstadoCambiado {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(estadoAnterior, "estadoAnterior");
    }

    public TicketEstadoCambiado(Instant occurredAt, TicketSnapshot ticket, EstadoTicket estadoAnterior) {
        this(UUID.randomUUID(), occurredAt, ticket, estadoAnterior);
    }

    @Override
    public String tipo() {
        return TIPO;
    }
}
