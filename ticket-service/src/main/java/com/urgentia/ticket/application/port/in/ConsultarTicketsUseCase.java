package com.urgentia.ticket.application.port.in;

import com.urgentia.ticket.application.port.out.FiltroTickets;
import com.urgentia.ticket.application.port.out.Pagina;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;

/** Consultas de tickets. Un SOLICITANTE solo ve los suyos. */
public interface ConsultarTicketsUseCase {

    Pagina<Ticket> buscar(FiltroTickets filtro, UsuarioActual usuario, int pagina, int tamanio);

    Ticket obtener(TicketId ticketId, UsuarioActual usuario);
}
