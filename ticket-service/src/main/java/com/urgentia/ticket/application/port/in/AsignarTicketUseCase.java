package com.urgentia.ticket.application.port.in;

import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import java.util.UUID;

/** Asignar un agente a un ticket CLASIFICADO o ESCALADO. */
public interface AsignarTicketUseCase {

    Ticket asignar(TicketId ticketId, UUID agenteId);
}
