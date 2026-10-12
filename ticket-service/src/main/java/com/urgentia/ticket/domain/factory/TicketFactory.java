package com.urgentia.ticket.domain.factory;

import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Factory: unico lugar donde nace un Ticket. Genera la identidad, toma la hora del
 * reloj y devuelve el agregado valido en estado NUEVO, con el evento TicketCreado registrado.
 */
public class TicketFactory {

    private final Clock reloj;

    public TicketFactory(Clock reloj) {
        this.reloj = Objects.requireNonNull(reloj, "El reloj es obligatorio");
    }

    public Ticket crear(String titulo, String descripcion, UUID solicitanteId) {
        return Ticket.crear(TicketId.nuevo(), titulo, descripcion, solicitanteId, Instant.now(reloj));
    }
}
