package com.urgentia.ticket.application.service;

import com.urgentia.ticket.application.exception.TicketNoEncontradoException;
import com.urgentia.ticket.application.port.in.AsignarTicketUseCase;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Busca el ticket, le pide al agregado que se asigne, guarda y publica. */
public class AsignarTicketService implements AsignarTicketUseCase {

    private final TicketRepository repositorio;
    private final EventPublisherPort publicador;
    private final Clock reloj;

    public AsignarTicketService(TicketRepository repositorio, EventPublisherPort publicador, Clock reloj) {
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Override
    public Ticket asignar(TicketId ticketId, UUID agenteId) {
        Ticket ticket = repositorio.buscarPorId(ticketId)
                .orElseThrow(() -> new TicketNoEncontradoException(ticketId));
        ticket.asignarA(agenteId, Instant.now(reloj));
        Ticket guardado = repositorio.guardar(ticket);
        publicador.publicar(ticket.extraerEventos());
        return guardado;
    }
}
