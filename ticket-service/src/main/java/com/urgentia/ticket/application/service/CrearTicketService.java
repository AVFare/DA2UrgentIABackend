package com.urgentia.ticket.application.service;

import com.urgentia.ticket.application.exception.ClasificacionNoDisponibleException;
import com.urgentia.ticket.application.port.in.CrearTicketUseCase;
import com.urgentia.ticket.application.port.out.ClasificadorPort;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.factory.TicketFactory;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.service.PrioridadStrategy;
import java.time.Clock;
import java.time.Instant;

/**
 * Orquesta la creacion (seccion 11.3): crear con la factory, pedir la clasificacion,
 * aplicarla (o dejar el ticket pendiente si la IA fallo), guardar y publicar los eventos.
 * Las reglas estan en el agregado: aca no se decide nada.
 */
public class CrearTicketService implements CrearTicketUseCase {

    private final TicketFactory factory;
    private final ClasificadorPort clasificador;
    private final PrioridadStrategy estrategia;
    private final TicketRepository repositorio;
    private final EventPublisherPort publicador;
    private final Clock reloj;

    public CrearTicketService(TicketFactory factory, ClasificadorPort clasificador, PrioridadStrategy estrategia,
                              TicketRepository repositorio, EventPublisherPort publicador, Clock reloj) {
        this.factory = factory;
        this.clasificador = clasificador;
        this.estrategia = estrategia;
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Override
    public Ticket crear(CrearTicketCommand comando) {
        Ticket ticket = factory.crear(comando.titulo(), comando.descripcion(), comando.solicitanteId());
        try {
            Clasificacion clasificacion = clasificador.clasificar(ticket.id(), ticket.titulo(), ticket.descripcion());
            ticket.aplicarClasificacion(clasificacion, estrategia, Instant.now(reloj));
        } catch (ClasificacionNoDisponibleException e) {
            // La creacion nunca falla por culpa de la IA: el ticket queda para reclasificar.
            ticket.marcarPendienteDeClasificacion(Instant.now(reloj));
        }
        Ticket guardado = repositorio.guardar(ticket);
        publicador.publicar(ticket.extraerEventos());
        return guardado;
    }
}
