package com.urgentia.ticket.application.service;

import com.urgentia.ticket.application.port.in.AplicarClasificacionUseCase;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.application.port.out.MensajesProcesadosPort;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.service.PrioridadStrategy;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Aplica una ClasificacionGenerada que llego por la cola.
 *
 * <ul>
 *   <li>Idempotente: si el mensaje ya se proceso, no hace nada.</li>
 *   <li>Solo toca tickets en PENDIENTE_CLASIFICACION: si un agente ya lo reclasifico por REST,
 *       la respuesta de la cola llega tarde y se ignora.</li>
 *   <li>Si el ticket no existe, no hay nada que aplicar: se confirma sin efectos. Reintentarlo o
 *       mandarlo a la DLQ no lo arreglaria (el mensaje cumple el contrato).</li>
 *   <li>La regla es la del dominio, igual que por REST: Ticket.aplicarClasificacion calcula la
 *       prioridad con la matriz, el SLA y el escalamiento.</li>
 * </ul>
 */
public class AplicarClasificacionService implements AplicarClasificacionUseCase {

    static final String TIPO_MENSAJE = "ClasificacionGenerada";

    private final TicketRepository repositorio;
    private final MensajesProcesadosPort mensajes;
    private final PrioridadStrategy estrategia;
    private final EventPublisherPort publicador;
    private final Clock reloj;

    public AplicarClasificacionService(TicketRepository repositorio, MensajesProcesadosPort mensajes,
                                       PrioridadStrategy estrategia, EventPublisherPort publicador, Clock reloj) {
        this.repositorio = repositorio;
        this.mensajes = mensajes;
        this.estrategia = estrategia;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Override
    public Resultado aplicar(ClasificacionRecibida recibida) {
        if (mensajes.yaProcesado(recibida.mensajeId())) {
            return Resultado.DUPLICADA;
        }
        Optional<Ticket> encontrado = repositorio.buscarPorId(recibida.ticketId());
        if (encontrado.isEmpty()) {
            return Resultado.TICKET_INEXISTENTE;
        }
        Ticket ticket = encontrado.get();

        Resultado resultado;
        if (ticket.estado() != EstadoTicket.PENDIENTE_CLASIFICACION) {
            resultado = Resultado.IGNORADA;
        } else if (recibida.clasificacion() == null) {
            resultado = Resultado.QUEDA_PENDIENTE;
        } else {
            ticket.aplicarClasificacion(recibida.clasificacion(), estrategia, Instant.now(reloj));
            repositorio.guardar(ticket);
            publicador.publicar(ticket.extraerEventos());
            resultado = Resultado.APLICADA;
        }
        mensajes.registrar(recibida.mensajeId(), TIPO_MENSAJE);
        return resultado;
    }
}
