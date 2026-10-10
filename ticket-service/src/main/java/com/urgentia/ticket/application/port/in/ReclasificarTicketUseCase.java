package com.urgentia.ticket.application.port.in;

import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;

/** Volver a pedir la clasificacion a la IA (ticket PENDIENTE_CLASIFICACION o CLASIFICADO). */
public interface ReclasificarTicketUseCase {

    Ticket reclasificar(TicketId ticketId);
}
