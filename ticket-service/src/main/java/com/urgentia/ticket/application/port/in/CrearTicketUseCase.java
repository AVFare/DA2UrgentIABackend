package com.urgentia.ticket.application.port.in;

import com.urgentia.ticket.domain.model.Ticket;
import java.util.UUID;

/** Crear un ticket: se clasifica con la IA en la misma llamada y, si es critico, se escala. */
public interface CrearTicketUseCase {

    Ticket crear(CrearTicketCommand comando);

    record CrearTicketCommand(String titulo, String descripcion, UUID solicitanteId) {
    }
}
