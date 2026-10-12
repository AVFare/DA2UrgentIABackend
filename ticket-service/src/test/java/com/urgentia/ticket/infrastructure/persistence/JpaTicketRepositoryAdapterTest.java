package com.urgentia.ticket.infrastructure.persistence;

import static com.urgentia.ticket.domain.ClasificacionBuilder.laDeLaDemo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.application.port.out.FiltroTickets;
import com.urgentia.ticket.application.port.out.Pagina;
import com.urgentia.ticket.domain.TicketBuilder;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.domain.service.MatrizItilStrategy;
import com.urgentia.ticket.infrastructure.IntegracionBase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

/** El adaptador JPA guarda y lee el agregado completo, y no deja que dos cambios se pisen. */
class JpaTicketRepositoryAdapterTest extends IntegracionBase {

    @Autowired
    private JpaTicketRepositoryAdapter repositorio;

    @Test
    void guardaYLeeElTicketConTodosSusDatos() {
        Ticket ticket = TicketBuilder.unTicket().crear();
        ticket.aplicarClasificacion(laDeLaDemo().build(), new MatrizItilStrategy(), TicketBuilder.AHORA);

        Ticket guardado = repositorio.guardar(ticket);
        Ticket leido = repositorio.buscarPorId(ticket.id()).orElseThrow();

        assertThat(guardado.version()).isZero();
        assertThat(leido.version()).isZero();
        assertThat(leido.titulo()).isEqualTo(ticket.titulo());
        assertThat(leido.descripcion()).isEqualTo(ticket.descripcion());
        assertThat(leido.solicitanteId()).isEqualTo(ticket.solicitanteId());
        assertThat(leido.estado()).isEqualTo(EstadoTicket.ESCALADO);
        assertThat(leido.prioridad()).isEqualTo(ticket.prioridad());
        assertThat(leido.fechaLimiteSla()).isEqualTo(ticket.fechaLimiteSla());
        assertThat(leido.motivoEscalamiento()).isEqualTo("Prioridad P1");
        assertThat(leido.clasificacion()).isEqualTo(ticket.clasificacion());
        assertThat(leido.fechaCreacion()).isEqualTo(ticket.fechaCreacion());
        assertThat(leido.fechaActualizacion()).isEqualTo(ticket.fechaActualizacion());
        assertThat(leido.eventosPendientes()).isEmpty();
    }

    @Test
    void unTicketSinClasificarSeGuardaConNulos() {
        Ticket ticket = TicketBuilder.unTicket().crear();
        ticket.marcarPendienteDeClasificacion(TicketBuilder.AHORA);

        repositorio.guardar(ticket);
        Ticket leido = repositorio.buscarPorId(ticket.id()).orElseThrow();

        assertThat(leido.estado()).isEqualTo(EstadoTicket.PENDIENTE_CLASIFICACION);
        assertThat(leido.prioridad()).isNull();
        assertThat(leido.clasificacion()).isNull();
        assertThat(leido.fechaLimiteSla()).isNull();
    }

    @Test
    void cadaGuardadoSubeLaVersion() {
        Ticket ticket = TicketBuilder.unTicket().crear();
        ticket.aplicarClasificacion(com.urgentia.ticket.domain.ClasificacionBuilder.unaClasificacion().build(),
                new MatrizItilStrategy(), TicketBuilder.AHORA);
        repositorio.guardar(ticket);

        Ticket leido = repositorio.buscarPorId(ticket.id()).orElseThrow();
        leido.asignarA(UUID.randomUUID(), Instant.now());
        Ticket guardado = repositorio.guardar(leido);

        assertThat(guardado.version()).isEqualTo(1L);
        assertThat(repositorio.buscarPorId(ticket.id()).orElseThrow().estado()).isEqualTo(EstadoTicket.ASIGNADO);
    }

    @Test
    void dosCambiosSimultaneosNoSePisan() {
        Ticket ticket = TicketBuilder.unTicket().crear();
        ticket.aplicarClasificacion(com.urgentia.ticket.domain.ClasificacionBuilder.unaClasificacion().build(),
                new MatrizItilStrategy(), TicketBuilder.AHORA);
        repositorio.guardar(ticket);

        Ticket primero = repositorio.buscarPorId(ticket.id()).orElseThrow();
        Ticket segundo = repositorio.buscarPorId(ticket.id()).orElseThrow();
        primero.asignarA(UUID.randomUUID(), Instant.now());
        segundo.asignarA(UUID.randomUUID(), Instant.now());

        repositorio.guardar(primero);
        assertThatThrownBy(() -> repositorio.guardar(segundo)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void buscarPorUnIdQueNoExisteDevuelveVacio() {
        assertThat(repositorio.buscarPorId(TicketId.nuevo())).isEmpty();
    }

    @Test
    void filtraPorSolicitante() {
        UUID otro = UUID.randomUUID();
        repositorio.guardar(TicketBuilder.unTicket().crear());
        repositorio.guardar(TicketBuilder.unTicket().deSolicitante(otro).crear());

        Pagina<Ticket> pagina = repositorio.buscar(FiltroTickets.sinFiltros().conSolicitante(otro), 0, 20);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().get(0).solicitanteId()).isEqualTo(otro);
    }
}
