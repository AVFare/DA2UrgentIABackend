package com.urgentia.ticket.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Se creo un ticket (queda NUEVO o, si la IA fallo, PENDIENTE_CLASIFICACION). */
public record TicketCreado(UUID eventId, Instant occurredAt, TicketSnapshot ticket) implements DomainEvent {

    public static final String TIPO = "TicketCreado";

    public TicketCreado {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(ticket, "ticket");
    }

    public TicketCreado(Instant occurredAt, TicketSnapshot ticket) {
        this(UUID.randomUUID(), occurredAt, ticket);
    }

    @Override
    public String tipo() {
        return TIPO;
    }
}
