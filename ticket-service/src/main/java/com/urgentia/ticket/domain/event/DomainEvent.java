package com.urgentia.ticket.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: algo que ya paso en el agregado Ticket.
 * El agregado los registra y la capa de aplicacion los publica despues de guardar.
 */
public sealed interface DomainEvent
        permits TicketCreado, TicketClasificado, TicketEscalado, TicketAsignado, TicketEstadoCambiado, TicketResuelto {

    /** Id unico del evento (los consumidores lo usan para la idempotencia). */
    UUID eventId();

    Instant occurredAt();

    TicketSnapshot ticket();

    /** Nombre del tipo de evento, igual al enum TipoEvento del contexto (ej.: "TicketEscalado"). */
    String tipo();
}
