package com.urgentia.ticket.application.port.in;

import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;

/** Cambio de estado manual. El motivo es obligatorio solo para pasar a ESCALADO. */
public interface CambiarEstadoUseCase {

    Ticket cambiarEstado(TicketId ticketId, EstadoTicket destino, String motivo);
}
