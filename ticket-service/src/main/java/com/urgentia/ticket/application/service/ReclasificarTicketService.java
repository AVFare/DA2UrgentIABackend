package com.urgentia.ticket.application.service;

import com.urgentia.ticket.application.exception.TicketNoEncontradoException;
import com.urgentia.ticket.application.port.in.ReclasificarTicketUseCase;
import com.urgentia.ticket.application.port.out.ClasificadorPort;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.domain.service.PrioridadStrategy;
import java.time.Clock;
import java.time.Instant;

/**
 * Vuelve a pedir la clasificacion. Si la IA no responde, se propaga
 * ClasificacionNoDisponibleException (503 IA_NO_DISPONIBLE) y el ticket no cambia.
 */
public class ReclasificarTicketService implements ReclasificarTicketUseCase {

    private final ClasificadorPort clasificador;
    private final PrioridadStrategy estrategia;
    private final TicketRepository repositorio;
    private final EventPublisherPort publicador;
    private final Clock reloj;

    public ReclasificarTicketService(ClasificadorPort clasificador, PrioridadStrategy estrategia,
                                     TicketRepository repositorio, EventPublisherPort publicador, Clock reloj) {
        this.clasificador = clasificador;
        this.estrategia = estrategia;
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Override
    public Ticket reclasificar(TicketId ticketId) {
        Ticket ticket = repositorio.buscarPorId(ticketId)
                .orElseThrow(() -> new TicketNoEncontradoException(ticketId));
        // Si el estado no lo permite, se corta antes de llamar a la IA.
        ticket.verificarQueAdmiteClasificacion();
        Clasificacion clasificacion = clasificador.clasificar(ticket.id(), ticket.titulo(), ticket.descripcion());
        ticket.aplicarClasificacion(clasificacion, estrategia, Instant.now(reloj));
        Ticket guardado = repositorio.guardar(ticket);
        publicador.publicar(ticket.extraerEventos());
        return guardado;
    }
}
