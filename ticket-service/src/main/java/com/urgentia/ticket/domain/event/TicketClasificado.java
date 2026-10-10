package com.urgentia.ticket.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Se aplico una clasificacion de la IA y se calculo prioridad y SLA. */
public record TicketClasificado(UUID eventId, Instant occurredAt, TicketSnapshot ticket) implements DomainEvent {

    public static final String TIPO = "TicketClasificado";

    public TicketClasificado {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(ticket, "ticket");
    }

    public TicketClasificado(Instant occurredAt, TicketSnapshot ticket) {
        this(UUID.randomUUID(), occurredAt, ticket);
    }

    @Override
    public String tipo() {
        return TIPO;
    }
}
