package com.urgentia.ticket.application.service;

import com.urgentia.ticket.application.exception.ClasificacionNoDisponibleException;
import com.urgentia.ticket.application.port.in.CrearTicketUseCase;
import com.urgentia.ticket.application.port.out.ClasificadorPort;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.application.port.out.SolicitudDeClasificacionPort;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.factory.TicketFactory;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.service.PrioridadStrategy;
import java.time.Clock;
import java.time.Instant;

/**
 * Orquesta la creacion (seccion 11.3): crear con la factory, pedir la clasificacion, guardar y
 * publicar los eventos. Segun CLASIFICACION_MODO, la clasificacion se pide por la cola (async, la
 * respuesta llega despues) o por REST dentro del alta (sync, el plan B; si la IA falla, el ticket
 * queda pendiente). Las reglas estan en el agregado: aca no se decide nada.
 */
public class CrearTicketService implements CrearTicketUseCase {

    private final TicketFactory factory;
    private final ClasificadorPort clasificador;
    private final PrioridadStrategy estrategia;
    private final TicketRepository repositorio;
    private final EventPublisherPort publicador;
    private final SolicitudDeClasificacionPort solicitudes;
    private final ModoClasificacion modo;
    private final Clock reloj;

    public CrearTicketService(TicketFactory factory, ClasificadorPort clasificador, PrioridadStrategy estrategia,
                              TicketRepository repositorio, EventPublisherPort publicador,
                              SolicitudDeClasificacionPort solicitudes, ModoClasificacion modo, Clock reloj) {
        this.factory = factory;
        this.clasificador = clasificador;
        this.estrategia = estrategia;
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.solicitudes = solicitudes;
        this.modo = modo;
        this.reloj = reloj;
    }

    @Override
    public Ticket crear(CrearTicketCommand comando) {
        Ticket ticket = factory.crear(comando.titulo(), comando.descripcion(), comando.solicitanteId());
        return modo == ModoClasificacion.ASINCRONICO ? crearYPedirPorLaCola(ticket) : crearClasificandoPorRest(ticket);
    }

    /**
     * Async: el ticket queda PENDIENTE_CLASIFICACION y el pedido a la IA sale por la cola, en la misma
     * transaccion. La prioridad la pone AplicarClasificacionService cuando llega la respuesta.
     */
    private Ticket crearYPedirPorLaCola(Ticket ticket) {
        ticket.marcarPendienteDeClasificacion(Instant.now(reloj));
        Ticket guardado = repositorio.guardar(ticket);
        publicador.publicar(ticket.extraerEventos());
        solicitudes.solicitar(ticket.id(), ticket.titulo(), ticket.descripcion());
        return guardado;
    }

    /** Sync (plan B): la IA se llama por REST dentro del alta, como en la Entrega 1. */
    private Ticket crearClasificandoPorRest(Ticket ticket) {
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
