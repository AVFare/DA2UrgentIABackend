package com.urgentia.ticket.domain.model;

import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.domain.event.TicketAsignado;
import com.urgentia.ticket.domain.event.TicketClasificado;
import com.urgentia.ticket.domain.event.TicketCreado;
import com.urgentia.ticket.domain.event.TicketEscalado;
import com.urgentia.ticket.domain.event.TicketEstadoCambiado;
import com.urgentia.ticket.domain.event.TicketResuelto;
import com.urgentia.ticket.domain.event.TicketSnapshot;
import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import com.urgentia.ticket.domain.exception.TransicionInvalidaException;
import com.urgentia.ticket.domain.service.PrioridadStrategy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Agregado Ticket (Aggregate Root). Es el unico lugar donde viven las reglas de
 * negocio de la seccion 8 del contexto: prioridad, SLA, escalamiento y estados.
 *
 * <p>No tiene setters: el estado cambia solo a traves de sus metodos de negocio,
 * que validan la transicion y registran los eventos de dominio que corresponden.
 * Los eventos se retiran con {@link #extraerEventos()} despues de guardar.
 *
 * <p>Java puro: no importa Spring, JPA ni Jackson.
 */
public class Ticket {

    public static final int TITULO_MIN = 5;
    public static final int TITULO_MAX = 120;
    public static final int DESCRIPCION_MIN = 10;
    public static final int DESCRIPCION_MAX = 2000;

    /** Motivo del escalamiento automatico cuando la matriz da P1. */
    public static final String MOTIVO_PRIORIDAD_P1 = "Prioridad P1";
    /** Motivo del escalamiento automatico cuando la IA marca el caso como critico y la matriz no daba P1. */
    public static final String MOTIVO_CRITICO_IA = "Marcado como crítico por la IA";

    private static final Set<EstadoTicket> CLASIFICABLES =
            EnumSet.of(EstadoTicket.NUEVO, EstadoTicket.PENDIENTE_CLASIFICACION, EstadoTicket.CLASIFICADO);

    private final TicketId id;
    private final String titulo;
    private final String descripcion;
    private final UUID solicitanteId;
    private final Instant fechaCreacion;
    private final Long version;

    private UUID agenteAsignadoId;
    private EstadoTicket estado;
    private Prioridad prioridad;
    private Sla sla;
    private Clasificacion clasificacion;
    private boolean requiereRevisionManual;
    private String motivoEscalamiento;
    private Instant fechaActualizacion;

    private final List<DomainEvent> eventos = new ArrayList<>();

    private Ticket(
            TicketId id,
            String titulo,
            String descripcion,
            UUID solicitanteId,
            UUID agenteAsignadoId,
            EstadoTicket estado,
            Prioridad prioridad,
            Sla sla,
            Clasificacion clasificacion,
            boolean requiereRevisionManual,
            String motivoEscalamiento,
            Instant fechaCreacion,
            Instant fechaActualizacion,
            Long version) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.solicitanteId = solicitanteId;
        this.agenteAsignadoId = agenteAsignadoId;
        this.estado = estado;
        this.prioridad = prioridad;
        this.sla = sla;
        this.clasificacion = clasificacion;
        this.requiereRevisionManual = requiereRevisionManual;
        this.motivoEscalamiento = motivoEscalamiento;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaActualizacion;
        this.version = version;
    }

    // ------------------------------------------------------------------ creacion

    /**
     * Crea un ticket NUEVO y registra {@link TicketCreado}. Lo usa {@code TicketFactory},
     * que es quien genera el id y toma la hora del reloj.
     */
    public static Ticket crear(TicketId id, String titulo, String descripcion, UUID solicitanteId, Instant ahora) {
        requerido(id, "El id del ticket es obligatorio");
        requerido(solicitanteId, "El solicitante es obligatorio");
        requerido(ahora, "La fecha de creacion es obligatoria");
        validarTexto(titulo, "titulo", TITULO_MIN, TITULO_MAX);
        validarTexto(descripcion, "descripcion", DESCRIPCION_MIN, DESCRIPCION_MAX);

        Ticket ticket = new Ticket(id, titulo, descripcion, solicitanteId, null, EstadoTicket.NUEVO,
                null, null, null, false, null, ahora, ahora, null);
        ticket.registrar(new TicketCreado(ahora, ticket.snapshot()));
        return ticket;
    }

    /**
     * Rearma un ticket que ya existia (por ejemplo, leido de la base). No valida
     * transiciones ni registra eventos: el ticket ya paso por las reglas cuando se guardo.
     */
    public static Ticket reconstituir(
            TicketId id,
            String titulo,
            String descripcion,
            UUID solicitanteId,
            UUID agenteAsignadoId,
            EstadoTicket estado,
            Prioridad prioridad,
            Instant fechaLimiteSla,
            Clasificacion clasificacion,
            boolean requiereRevisionManual,
            String motivoEscalamiento,
            Instant fechaCreacion,
            Instant fechaActualizacion,
            Long version) {
        requerido(id, "El id del ticket es obligatorio");
        requerido(titulo, "El titulo es obligatorio");
        requerido(descripcion, "La descripcion es obligatoria");
        requerido(solicitanteId, "El solicitante es obligatorio");
        requerido(estado, "El estado es obligatorio");
        requerido(fechaCreacion, "La fecha de creacion es obligatoria");
        requerido(fechaActualizacion, "La fecha de actualizacion es obligatoria");
        if ((prioridad == null) != (fechaLimiteSla == null)) {
            throw new ReglaDeNegocioException("Prioridad y SLA van juntos: o estan los dos o ninguno");
        }
        Sla sla = prioridad == null ? null : new Sla(prioridad, fechaLimiteSla);
        return new Ticket(id, titulo, descripcion, solicitanteId, agenteAsignadoId, estado, prioridad, sla,
                clasificacion, requiereRevisionManual, motivoEscalamiento, fechaCreacion, fechaActualizacion, version);
    }

    // ------------------------------------------------------------------ clasificacion

    /**
     * Aplica lo que sugirio la IA (seccion 8.3):
     * <ol>
     *   <li>calcula la prioridad con la estrategia (matriz ITIL);</li>
     *   <li>si la IA marco {@code requiereEscalamiento}, la prioridad se fuerza a P1;</li>
     *   <li>calcula el SLA desde la fecha de la clasificacion;</li>
     *   <li>si la confianza es menor a 0.6, marca {@code requiereRevisionManual};</li>
     *   <li>si la prioridad final es P1, escala el ticket.</li>
     * </ol>
     * Se puede aplicar a un ticket NUEVO, PENDIENTE_CLASIFICACION o CLASIFICADO (reclasificacion).
     */
    public void aplicarClasificacion(Clasificacion nueva, PrioridadStrategy estrategia, Instant ahora) {
        requerido(nueva, "La clasificacion es obligatoria");
        requerido(estrategia, "La estrategia de prioridad es obligatoria");
        requerido(ahora, "La fecha es obligatoria");
        verificarQueAdmiteClasificacion();

        Prioridad segunMatriz = estrategia.calcular(nueva.urgencia(), nueva.impacto());
        Prioridad prioridadFinal = nueva.requiereEscalamiento() ? Prioridad.P1 : segunMatriz;

        this.clasificacion = nueva;
        this.prioridad = prioridadFinal;
        this.sla = Sla.calcular(prioridadFinal, nueva.fecha());
        this.requiereRevisionManual = nueva.requiereRevisionManual();
        this.estado = EstadoTicket.CLASIFICADO;
        this.fechaActualizacion = ahora;
        registrar(new TicketClasificado(ahora, snapshot()));

        if (prioridadFinal == Prioridad.P1) {
            String motivo = segunMatriz == Prioridad.P1 ? MOTIVO_PRIORIDAD_P1 : MOTIVO_CRITICO_IA;
            escalarAutomaticamente(motivo, ahora);
        }
    }

    /**
     * Lanza {@link TransicionInvalidaException} si el ticket no se puede (re)clasificar.
     * Sirve para no llamar a la IA en vano cuando el estado ya no lo permite.
     */
    public void verificarQueAdmiteClasificacion() {
        if (!CLASIFICABLES.contains(estado)) {
            throw new TransicionInvalidaException(estado, EstadoTicket.CLASIFICADO,
                    "No se puede clasificar un ticket en estado " + estado
                            + ": solo NUEVO, PENDIENTE_CLASIFICACION o CLASIFICADO");
        }
    }

    /** La IA fallo o no respondio a tiempo al crear el ticket: queda esperando una reclasificacion. */
    public void marcarPendienteDeClasificacion(Instant ahora) {
        requerido(ahora, "La fecha es obligatoria");
        validarTransicion(EstadoTicket.PENDIENTE_CLASIFICACION, EnumSet.of(EstadoTicket.NUEVO));
        this.estado = EstadoTicket.PENDIENTE_CLASIFICACION;
        this.fechaActualizacion = ahora;
    }

    // ------------------------------------------------------------------ asignacion

    /**
     * Asigna un agente. En CLASIFICADO el ticket pasa a ASIGNADO; en ESCALADO se asigna
     * (o reasigna) sin cambiar el estado, para que la guardia lo tome. En cualquier otro estado es invalido.
     */
    public void asignarA(UUID agenteId, Instant ahora) {
        requerido(agenteId, "El agente es obligatorio");
        requerido(ahora, "La fecha es obligatoria");
        if (estado == EstadoTicket.CLASIFICADO) {
            EstadoTicket anterior = estado;
            this.agenteAsignadoId = agenteId;
            this.estado = EstadoTicket.ASIGNADO;
            this.fechaActualizacion = ahora;
            registrar(new TicketAsignado(ahora, snapshot()));
            registrar(new TicketEstadoCambiado(ahora, snapshot(), anterior));
        } else if (estado == EstadoTicket.ESCALADO) {
            this.agenteAsignadoId = agenteId;
            this.fechaActualizacion = ahora;
            registrar(new TicketAsignado(ahora, snapshot()));
        } else {
            throw new TransicionInvalidaException(estado, EstadoTicket.ASIGNADO,
                    "No se puede asignar un ticket en estado " + estado + ": solo en CLASIFICADO o ESCALADO");
        }
    }

    // ------------------------------------------------------------------ cambios de estado

    /**
     * Punto de entrada del cambio de estado manual (PATCH /estado). Delega en el metodo
     * de negocio que corresponde al estado destino.
     *
     * @param motivo obligatorio solo si el destino es ESCALADO
     */
    public void cambiarEstado(EstadoTicket destino, String motivo, Instant ahora) {
        requerido(destino, "El estado destino es obligatorio");
        switch (destino) {
            case EN_CURSO -> {
                if (estado == EstadoTicket.RESUELTO) {
                    reabrir(ahora);
                } else {
                    iniciar(ahora);
                }
            }
            case ESCALADO -> escalar(motivo, ahora);
            case RESUELTO -> resolver(ahora);
            case CERRADO -> cerrar(ahora);
            case ASIGNADO -> throw new TransicionInvalidaException(estado, destino,
                    "No se puede pasar de " + estado + " a ASIGNADO con un cambio de estado: hay que asignar un agente");
            default -> throw new TransicionInvalidaException(estado, destino);
        }
    }

    /** ASIGNADO o ESCALADO pasan a EN_CURSO (el agente o la guardia lo toma). Requiere agente. */
    public void iniciar(Instant ahora) {
        cambiarA(EstadoTicket.EN_CURSO, EnumSet.of(EstadoTicket.ASIGNADO, EstadoTicket.ESCALADO), true, ahora);
    }

    /**
     * Escalamiento manual desde ASIGNADO o EN_CURSO. Desde CLASIFICADO el escalamiento
     * es solo automatico (al aplicar la clasificacion).
     */
    public void escalar(String motivo, Instant ahora) {
        validarTransicion(EstadoTicket.ESCALADO, EnumSet.of(EstadoTicket.ASIGNADO, EstadoTicket.EN_CURSO));
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaDeNegocioException("El motivo es obligatorio para escalar un ticket");
        }
        String motivoLimpio = motivo.strip();
        EstadoTicket anterior = moverA(EstadoTicket.ESCALADO,
                EnumSet.of(EstadoTicket.ASIGNADO, EstadoTicket.EN_CURSO), false, ahora);
        this.motivoEscalamiento = motivoLimpio;
        registrar(new TicketEstadoCambiado(ahora, snapshot(), anterior));
        registrar(new TicketEscalado(ahora, snapshot(), anterior, motivoLimpio));
    }

    /** EN_CURSO pasa a RESUELTO. Requiere agente. */
    public void resolver(Instant ahora) {
        EstadoTicket anterior = moverA(EstadoTicket.RESUELTO, EnumSet.of(EstadoTicket.EN_CURSO), true, ahora);
        registrar(new TicketEstadoCambiado(ahora, snapshot(), anterior));
        registrar(new TicketResuelto(ahora, snapshot(), anterior));
    }

    /** RESUELTO pasa a CERRADO (estado final). */
    public void cerrar(Instant ahora) {
        cambiarA(EstadoTicket.CERRADO, EnumSet.of(EstadoTicket.RESUELTO), false, ahora);
    }

    /** RESUELTO vuelve a EN_CURSO. Requiere agente. */
    public void reabrir(Instant ahora) {
        cambiarA(EstadoTicket.EN_CURSO, EnumSet.of(EstadoTicket.RESUELTO), true, ahora);
    }

    // ------------------------------------------------------------------ consultas

    /** SLA vencido: la fecha limite ya paso y el ticket sigue abierto (seccion 8.2). */
    public boolean slaVencido(Instant ahora) {
        return sla != null && estado.estaAbierto() && sla.vencidoA(ahora);
    }

    /** Foto del ticket que viaja en los eventos (sin la descripcion). */
    public TicketSnapshot snapshot() {
        return new TicketSnapshot(
                id.valor(),
                titulo,
                estado,
                prioridad,
                clasificacion == null ? null : clasificacion.categoria(),
                clasificacion == null ? null : clasificacion.moduloAfectado(),
                solicitanteId,
                agenteAsignadoId,
                fechaCreacion,
                fechaLimiteSla());
    }

    /** Devuelve los eventos registrados desde la ultima extraccion y vacia la lista. */
    public List<DomainEvent> extraerEventos() {
        List<DomainEvent> copia = List.copyOf(eventos);
        eventos.clear();
        return copia;
    }

    /** Eventos registrados todavia no extraidos (solo lectura). */
    public List<DomainEvent> eventosPendientes() {
        return List.copyOf(eventos);
    }

    public TicketId id() {
        return id;
    }

    public String titulo() {
        return titulo;
    }

    public String descripcion() {
        return descripcion;
    }

    public UUID solicitanteId() {
        return solicitanteId;
    }

    /** null si todavia no tiene agente. */
    public UUID agenteAsignadoId() {
        return agenteAsignadoId;
    }

    public EstadoTicket estado() {
        return estado;
    }

    /** null si el ticket no esta clasificado. */
    public Prioridad prioridad() {
        return prioridad;
    }

    /** null si el ticket no esta clasificado. */
    public Sla sla() {
        return sla;
    }

    /** null si el ticket no esta clasificado. */
    public Instant fechaLimiteSla() {
        return sla == null ? null : sla.fechaLimite();
    }

    /** null si el ticket no esta clasificado. */
    public Clasificacion clasificacion() {
        return clasificacion;
    }

    public boolean requiereRevisionManual() {
        return requiereRevisionManual;
    }

    /** null si el ticket nunca se escalo. */
    public String motivoEscalamiento() {
        return motivoEscalamiento;
    }

    public Instant fechaCreacion() {
        return fechaCreacion;
    }

    public Instant fechaActualizacion() {
        return fechaActualizacion;
    }

    /** Version para el control de concurrencia optimista. null si nunca se guardo. */
    public Long version() {
        return version;
    }

    // ------------------------------------------------------------------ internos

    private void escalarAutomaticamente(String motivo, Instant ahora) {
        EstadoTicket anterior = moverA(EstadoTicket.ESCALADO, EnumSet.of(EstadoTicket.CLASIFICADO), false, ahora);
        this.motivoEscalamiento = motivo;
        registrar(new TicketEscalado(ahora, snapshot(), anterior, motivo));
    }

    /** Cambia el estado y registra {@link TicketEstadoCambiado}. */
    private void cambiarA(EstadoTicket destino, Set<EstadoTicket> origenes, boolean requiereAgente, Instant ahora) {
        EstadoTicket anterior = moverA(destino, origenes, requiereAgente, ahora);
        registrar(new TicketEstadoCambiado(ahora, snapshot(), anterior));
    }

    /**
     * Valida la transicion (y el agente si hace falta), cambia el estado y devuelve el anterior.
     * No registra eventos: lo hace quien la llama, en el orden que pide la seccion 8.5.
     */
    private EstadoTicket moverA(EstadoTicket destino, Set<EstadoTicket> origenes, boolean requiereAgente,
                                Instant ahora) {
        requerido(ahora, "La fecha es obligatoria");
        validarTransicion(destino, origenes);
        if (requiereAgente && agenteAsignadoId == null) {
            throw new ReglaDeNegocioException(
                    "Para pasar a " + destino + " el ticket tiene que tener un agente asignado");
        }
        EstadoTicket anterior = estado;
        this.estado = destino;
        this.fechaActualizacion = ahora;
        return anterior;
    }

    private void validarTransicion(EstadoTicket destino, Set<EstadoTicket> origenes) {
        if (!origenes.contains(estado) || !estado.puedePasarA(destino)) {
            throw new TransicionInvalidaException(estado, destino);
        }
    }

    private void registrar(DomainEvent evento) {
        eventos.add(evento);
    }

    private static void validarTexto(String valor, String campo, int minimo, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaDeNegocioException("El " + campo + " es obligatorio");
        }
        int largo = valor.length();
        if (largo < minimo || largo > maximo) {
            throw new ReglaDeNegocioException(
                    "El " + campo + " debe tener entre " + minimo + " y " + maximo + " caracteres y tiene " + largo);
        }
    }

    private static void requerido(Object valor, String mensaje) {
        if (valor == null) {
            throw new ReglaDeNegocioException(mensaje);
        }
    }

    @Override
    public boolean equals(Object otro) {
        return this == otro || (otro instanceof Ticket ticket && id.equals(ticket.id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Ticket[" + id + ", " + estado + ", " + prioridad + "]";
    }
}
