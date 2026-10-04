package com.urgentia.ticket.infrastructure.config;

import com.urgentia.ticket.application.port.in.AsignarTicketUseCase;
import com.urgentia.ticket.application.port.in.CambiarEstadoUseCase;
import com.urgentia.ticket.application.port.in.ConsultarTicketsUseCase;
import com.urgentia.ticket.application.port.in.CrearTicketUseCase;
import com.urgentia.ticket.application.port.in.ReclasificarTicketUseCase;
import com.urgentia.ticket.application.port.in.UsuarioActual;
import com.urgentia.ticket.application.port.out.FiltroTickets;
import com.urgentia.ticket.application.port.out.Pagina;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decorator: envuelve cada caso de uso en una transaccion. Asi la capa de aplicacion
 * queda sin Spring y la transaccion (un detalle tecnico) la pone la infraestructura.
 * Los eventos que publica el caso de uso salen recien cuando la transaccion confirma
 * (ver ReenvioDeEventosListener).
 */
public class CasosDeUsoTransaccionales implements CrearTicketUseCase, AsignarTicketUseCase, CambiarEstadoUseCase,
        ReclasificarTicketUseCase, ConsultarTicketsUseCase {

    private final CrearTicketUseCase crear;
    private final AsignarTicketUseCase asignar;
    private final CambiarEstadoUseCase cambiarEstado;
    private final ReclasificarTicketUseCase reclasificar;
    private final ConsultarTicketsUseCase consultar;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public CasosDeUsoTransaccionales(CrearTicketUseCase crear, AsignarTicketUseCase asignar,
                                     CambiarEstadoUseCase cambiarEstado, ReclasificarTicketUseCase reclasificar,
                                     ConsultarTicketsUseCase consultar, TransactionTemplate escritura,
                                     TransactionTemplate lectura) {
        this.crear = crear;
        this.asignar = asignar;
        this.cambiarEstado = cambiarEstado;
        this.reclasificar = reclasificar;
        this.consultar = consultar;
        this.escritura = escritura;
        this.lectura = lectura;
    }

    @Override
    public Ticket crear(CrearTicketCommand comando) {
        return escritura.execute(estado -> crear.crear(comando));
    }

    @Override
    public Ticket asignar(TicketId ticketId, UUID agenteId) {
        return escritura.execute(estado -> asignar.asignar(ticketId, agenteId));
    }

    @Override
    public Ticket cambiarEstado(TicketId ticketId, EstadoTicket destino, String motivo) {
        return escritura.execute(estado -> cambiarEstado.cambiarEstado(ticketId, destino, motivo));
    }

    @Override
    public Ticket reclasificar(TicketId ticketId) {
        return escritura.execute(estado -> reclasificar.reclasificar(ticketId));
    }

    @Override
    public Pagina<Ticket> buscar(FiltroTickets filtro, UsuarioActual usuario, int pagina, int tamanio) {
        return lectura.execute(estado -> consultar.buscar(filtro, usuario, pagina, tamanio));
    }

    @Override
    public Ticket obtener(TicketId ticketId, UsuarioActual usuario) {
        return lectura.execute(estado -> consultar.obtener(ticketId, usuario));
    }
}
