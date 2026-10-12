package com.urgentia.ticket.domain.model;

import static com.urgentia.ticket.domain.TicketBuilder.AGENTE;
import static com.urgentia.ticket.domain.TicketBuilder.AHORA;
import static com.urgentia.ticket.domain.TicketBuilder.unTicket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.domain.event.TicketEstadoCambiado;
import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import com.urgentia.ticket.domain.exception.TransicionInvalidaException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TicketAsignacionTest {

    private static final Instant DESPUES = AHORA.plusSeconds(60);

    @Test
    void enClasificadoPasaAAsignado() {
        Ticket ticket = unTicket().enEstado(EstadoTicket.CLASIFICADO).construir();

        ticket.asignarA(AGENTE, DESPUES);

        assertThat(ticket.estado()).isEqualTo(EstadoTicket.ASIGNADO);
        assertThat(ticket.agenteAsignadoId()).isEqualTo(AGENTE);
        assertThat(ticket.fechaActualizacion()).isEqualTo(DESPUES);
        assertThat(ticket.eventosPendientes()).extracting(DomainEvent::tipo)
                .containsExactly("TicketAsignado", "TicketEstadoCambiado");
        TicketEstadoCambiado cambio = (TicketEstadoCambiado) ticket.eventosPendientes().get(1);
        assertThat(cambio.estadoAnterior()).isEqualTo(EstadoTicket.CLASIFICADO);
        assertThat(cambio.ticket().agenteAsignadoId()).isEqualTo(AGENTE);
        assertThat(ticket.eventosPendientes().get(0).ticket().estado()).isEqualTo(EstadoTicket.ASIGNADO);
    }

    @Test
    void enEscaladoAsignaSinCambiarElEstado() {
        Ticket ticket = unTicket().enEstado(EstadoTicket.ESCALADO).construir();

        ticket.asignarA(AGENTE, DESPUES);

        assertThat(ticket.estado()).isEqualTo(EstadoTicket.ESCALADO);
        assertThat(ticket.agenteAsignadoId()).isEqualTo(AGENTE);
        assertThat(ticket.eventosPendientes()).extracting(DomainEvent::tipo).containsExactly("TicketAsignado");
    }

    @Test
    void enEscaladoSePuedeReasignar() {
        UUID otroAgente = UUID.randomUUID();
        Ticket ticket = unTicket().enEstado(EstadoTicket.ESCALADO).conAgente(AGENTE).construir();

        ticket.asignarA(otroAgente, DESPUES);

        assertThat(ticket.agenteAsignadoId()).isEqualTo(otroAgente);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTicket.class,
            names = {"NUEVO", "PENDIENTE_CLASIFICACION", "ASIGNADO", "EN_CURSO", "RESUELTO", "CERRADO"})
    void enOtrosEstadosEsUnaTransicionInvalida(EstadoTicket estado) {
        Ticket ticket = unTicket().enEstado(estado).conAgente(AGENTE).construir();

        assertThatThrownBy(() -> ticket.asignarA(UUID.randomUUID(), DESPUES))
                .isInstanceOf(TransicionInvalidaException.class)
                .satisfies(e -> {
                    TransicionInvalidaException t = (TransicionInvalidaException) e;
                    assertThat(t.desde()).isEqualTo(estado);
                    assertThat(t.hacia()).isEqualTo(EstadoTicket.ASIGNADO);
                });
        assertThat(ticket.estado()).isEqualTo(estado);
        assertThat(ticket.eventosPendientes()).isEmpty();
    }

    @Test
    void elAgenteEsObligatorio() {
        Ticket ticket = unTicket().enEstado(EstadoTicket.CLASIFICADO).construir();

        assertThatThrownBy(() -> ticket.asignarA(null, DESPUES)).isInstanceOf(ReglaDeNegocioException.class);
    }
}
