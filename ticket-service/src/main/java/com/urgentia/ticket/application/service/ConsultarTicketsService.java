package com.urgentia.ticket.application.service;

import com.urgentia.ticket.application.exception.TicketNoEncontradoException;
import com.urgentia.ticket.application.port.in.ConsultarTicketsUseCase;
import com.urgentia.ticket.application.port.in.UsuarioActual;
import com.urgentia.ticket.application.port.out.FiltroTickets;
import com.urgentia.ticket.application.port.out.Pagina;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;

/**
 * Consultas. Un SOLICITANTE solo ve sus propios tickets: en el listado se filtra por
 * su id y, si pide el detalle de un ticket ajeno, recibe 404 (no se revela que existe).
 */
public class ConsultarTicketsService implements ConsultarTicketsUseCase {

    private final TicketRepository repositorio;

    public ConsultarTicketsService(TicketRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Pagina<Ticket> buscar(FiltroTickets filtro, UsuarioActual usuario, int pagina, int tamanio) {
        FiltroTickets efectivo = usuario.soloVeLosSuyos() ? filtro.conSolicitante(usuario.id()) : filtro;
        return repositorio.buscar(efectivo, pagina, tamanio);
    }

    @Override
    public Ticket obtener(TicketId ticketId, UsuarioActual usuario) {
        return repositorio.buscarPorId(ticketId)
                .filter(ticket -> !usuario.soloVeLosSuyos() || ticket.solicitanteId().equals(usuario.id()))
                .orElseThrow(() -> new TicketNoEncontradoException(ticketId));
    }
}
