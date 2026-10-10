package com.urgentia.ticket.application.service;

import com.urgentia.ticket.application.exception.TicketNoEncontradoException;
import com.urgentia.ticket.application.port.in.CambiarEstadoUseCase;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import java.time.Clock;
import java.time.Instant;

/** Busca el ticket, le pide al agregado el cambio de estado, guarda y publica. */
public class CambiarEstadoService implements CambiarEstadoUseCase {

    private final TicketRepository repositorio;
    private final EventPublisherPort publicador;
    private final Clock reloj;

    public CambiarEstadoService(TicketRepository repositorio, EventPublisherPort publicador, Clock reloj) {
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Override
    public Ticket cambiarEstado(TicketId ticketId, EstadoTicket destino, String motivo) {
        Ticket ticket = repositorio.buscarPorId(ticketId)
                .orElseThrow(() -> new TicketNoEncontradoException(ticketId));
        ticket.cambiarEstado(destino, motivo, Instant.now(reloj));
        Ticket guardado = repositorio.guardar(ticket);
        publicador.publicar(ticket.extraerEventos());
        return guardado;
    }
}
