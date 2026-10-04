package com.urgentia.ticket.application;

import com.urgentia.ticket.application.exception.ClasificacionNoDisponibleException;
import com.urgentia.ticket.application.port.out.ClasificadorPort;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.application.port.out.FiltroTickets;
import com.urgentia.ticket.application.port.out.Pagina;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dobles de prueba de los puertos de salida (sin Mockito, para que se lean facil). */
public final class Dobles {

    private Dobles() {
    }

    /** Repositorio en memoria. Al guardar, devuelve una copia con la version incrementada, como JPA. */
    public static class RepositorioEnMemoria implements TicketRepository {

        private final Map<TicketId, Ticket> tickets = new LinkedHashMap<>();
        public int guardados = 0;
        public RuntimeException fallarAlGuardar;

        @Override
        public Ticket guardar(Ticket ticket) {
            if (fallarAlGuardar != null) {
                throw fallarAlGuardar;
            }
            guardados++;
            long version = ticket.version() == null ? 0 : ticket.version() + 1;
            Ticket copia = copiar(ticket, version);
            tickets.put(ticket.id(), copia);
            return copia;
        }

        @Override
        public Optional<Ticket> buscarPorId(TicketId id) {
            return Optional.ofNullable(tickets.get(id)).map(t -> copiar(t, t.version()));
        }

        @Override
        public Pagina<Ticket> buscar(FiltroTickets filtro, int pagina, int tamanio) {
            List<Ticket> todos = tickets.values().stream()
                    .filter(t -> filtro.estado() == null || t.estado() == filtro.estado())
                    .filter(t -> filtro.prioridad() == null || t.prioridad() == filtro.prioridad())
                    .filter(t -> filtro.categoria() == null
                            || (t.clasificacion() != null && t.clasificacion().categoria() == filtro.categoria()))
                    .filter(t -> filtro.solicitanteId() == null || t.solicitanteId().equals(filtro.solicitanteId()))
                    .sorted(Comparator.comparing(Ticket::fechaCreacion).reversed())
                    .toList();
            int desde = Math.min(pagina * tamanio, todos.size());
            int hasta = Math.min(desde + tamanio, todos.size());
            int totalPaginas = (int) Math.ceil((double) todos.size() / tamanio);
            return new Pagina<>(todos.subList(desde, hasta), pagina, tamanio, todos.size(), totalPaginas);
        }

        private static Ticket copiar(Ticket t, Long version) {
            return Ticket.reconstituir(t.id(), t.titulo(), t.descripcion(), t.solicitanteId(), t.agenteAsignadoId(),
                    t.estado(), t.prioridad(), t.fechaLimiteSla(), t.clasificacion(), t.requiereRevisionManual(),
                    t.motivoEscalamiento(), t.fechaCreacion(), t.fechaActualizacion(), version);
        }
    }

    /** Clasificador que devuelve una clasificacion fija o simula que la IA esta caida. */
    public static class ClasificadorFalso implements ClasificadorPort {

        private Clasificacion respuesta;
        private boolean caido;
        public int llamadas = 0;

        public ClasificadorFalso responde(Clasificacion clasificacion) {
            this.respuesta = clasificacion;
            this.caido = false;
            return this;
        }

        public ClasificadorFalso caido() {
            this.caido = true;
            return this;
        }

        @Override
        public Clasificacion clasificar(TicketId ticketId, String titulo, String descripcion) {
            llamadas++;
            if (caido) {
                throw new ClasificacionNoDisponibleException("IA caida (simulada)");
            }
            return respuesta;
        }
    }

    /** Publicador que guarda lo que se publico y en que momento (cuantos guardados habia). */
    public static class PublicadorQueGraba implements EventPublisherPort {

        public final List<DomainEvent> publicados = new ArrayList<>();
        public final List<Integer> guardadosAlPublicar = new ArrayList<>();
        private final RepositorioEnMemoria repositorio;

        public PublicadorQueGraba(RepositorioEnMemoria repositorio) {
            this.repositorio = repositorio;
        }

        @Override
        public void publicar(List<DomainEvent> eventos) {
            publicados.addAll(eventos);
            guardadosAlPublicar.add(repositorio.guardados);
        }

        public List<String> tipos() {
            return publicados.stream().map(DomainEvent::tipo).toList();
        }
    }
}
