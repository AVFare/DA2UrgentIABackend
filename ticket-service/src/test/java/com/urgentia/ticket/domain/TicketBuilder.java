package com.urgentia.ticket.domain;

import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Prioridad;
import com.urgentia.ticket.domain.model.Sla;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Builder de tickets para los tests (patron Builder, seccion 13).
 * {@link #crear()} pasa por las reglas reales (Ticket.crear); {@link #construir()} arma
 * un ticket ya existente en cualquier estado, como si viniera de la base.
 */
public final class TicketBuilder {

    public static final Instant AHORA = Instant.parse("2026-10-05T14:03:10Z");
    public static final UUID SOLICITANTE = UUID.fromString("b1c2d3e4-0000-4000-8000-000000000004");
    public static final UUID AGENTE = UUID.fromString("b1c2d3e4-0000-4000-8000-000000000002");

    private static final EnumSet<EstadoTicket> SIN_CLASIFICAR =
            EnumSet.of(EstadoTicket.NUEVO, EstadoTicket.PENDIENTE_CLASIFICACION);

    private TicketId id = TicketId.nuevo();
    private String titulo = "No puede ingresar nadie";
    private String descripcion = "Producción caída, todos los usuarios bloqueados en el login desde las 9";
    private UUID solicitante = SOLICITANTE;
    private UUID agente = null;
    private EstadoTicket estado = EstadoTicket.NUEVO;
    private Clasificacion clasificacion = ClasificacionBuilder.unaClasificacion().build();

    private TicketBuilder() {
    }

    public static TicketBuilder unTicket() {
        return new TicketBuilder();
    }

    public TicketBuilder conTitulo(String titulo) {
        this.titulo = titulo;
        return this;
    }

    public TicketBuilder conDescripcion(String descripcion) {
        this.descripcion = descripcion;
        return this;
    }

    public TicketBuilder deSolicitante(UUID solicitante) {
        this.solicitante = solicitante;
        return this;
    }

    public TicketBuilder enEstado(EstadoTicket estado) {
        this.estado = estado;
        return this;
    }

    public TicketBuilder conAgente(UUID agente) {
        this.agente = agente;
        return this;
    }

    public TicketBuilder conClasificacion(Clasificacion clasificacion) {
        this.clasificacion = clasificacion;
        return this;
    }

    /** Crea un ticket NUEVO por el camino real (registra TicketCreado). */
    public Ticket crear() {
        return Ticket.crear(id, titulo, descripcion, solicitante, AHORA);
    }

    /** Arma un ticket existente en el estado pedido, sin eventos pendientes. */
    public Ticket construir() {
        boolean clasificado = !SIN_CLASIFICAR.contains(estado);
        Prioridad prioridad = clasificado ? Prioridad.P3 : null;
        Instant limite = clasificado ? Sla.calcular(Prioridad.P3, clasificacion.fecha()).fechaLimite() : null;
        return Ticket.reconstituir(id, titulo, descripcion, solicitante, agente, estado, prioridad, limite,
                clasificado ? clasificacion : null, false,
                estado == EstadoTicket.ESCALADO ? "Prioridad P1" : null, AHORA, AHORA, 0L);
    }
}
