package com.urgentia.ticket.domain.model;

import static com.urgentia.ticket.domain.TicketBuilder.AHORA;
import static com.urgentia.ticket.domain.TicketBuilder.unTicket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.domain.event.TicketCreado;
import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TicketCreacionTest {

    @Test
    void naceNuevoSinPrioridadNiSlaNiClasificacion() {
        Ticket ticket = unTicket().crear();

        assertThat(ticket.estado()).isEqualTo(EstadoTicket.NUEVO);
        assertThat(ticket.prioridad()).isNull();
        assertThat(ticket.sla()).isNull();
        assertThat(ticket.fechaLimiteSla()).isNull();
        assertThat(ticket.clasificacion()).isNull();
        assertThat(ticket.agenteAsignadoId()).isNull();
        assertThat(ticket.motivoEscalamiento()).isNull();
        assertThat(ticket.requiereRevisionManual()).isFalse();
        assertThat(ticket.fechaCreacion()).isEqualTo(AHORA);
    }

    @Test
    void registraTicketCreadoConLaFotoDelTicket() {
        Ticket ticket = unTicket().crear();

        DomainEvent evento = ticket.eventosPendientes().get(0);
        assertThat(evento).isInstanceOf(TicketCreado.class);
        assertThat(evento.tipo()).isEqualTo("TicketCreado");
        assertThat(evento.occurredAt()).isEqualTo(AHORA);
        assertThat(evento.eventId()).isNotNull();
        assertThat(evento.ticket().ticketId()).isEqualTo(ticket.id().valor());
        assertThat(evento.ticket().estado()).isEqualTo(EstadoTicket.NUEVO);
        assertThat(evento.ticket().titulo()).isEqualTo(ticket.titulo());
    }

    @ParameterizedTest(name = "titulo de {0} caracteres es valido")
    @ValueSource(ints = {5, 120})
    void aceptaTitulosEnLosBordes(int largo) {
        assertThat(unTicket().conTitulo("t".repeat(largo)).crear().titulo()).hasSize(largo);
    }

    @ParameterizedTest(name = "titulo de {0} caracteres es invalido")
    @ValueSource(ints = {4, 121})
    void rechazaTitulosFueraDeRango(int largo) {
        assertThatThrownBy(() -> unTicket().conTitulo("t".repeat(largo)).crear())
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("titulo");
    }

    @ParameterizedTest(name = "descripcion de {0} caracteres es valida")
    @ValueSource(ints = {10, 2000})
    void aceptaDescripcionesEnLosBordes(int largo) {
        assertThat(unTicket().conDescripcion("d".repeat(largo)).crear().descripcion()).hasSize(largo);
    }

    @ParameterizedTest(name = "descripcion de {0} caracteres es invalida")
    @ValueSource(ints = {9, 2001})
    void rechazaDescripcionesFueraDeRango(int largo) {
        assertThatThrownBy(() -> unTicket().conDescripcion("d".repeat(largo)).crear())
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("descripcion");
    }

    @Test
    void rechazaTextosVaciosONulos() {
        assertThatThrownBy(() -> unTicket().conTitulo(null).crear()).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> unTicket().conTitulo("        ").crear()).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> unTicket().conDescripcion(null).crear()).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> unTicket().conDescripcion("            ").crear())
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void elSolicitanteEsObligatorio() {
        assertThatThrownBy(() -> unTicket().deSolicitante(null).crear())
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("solicitante");
    }

    @Test
    void reconstituirNoRegistraEventos() {
        Ticket ticket = unTicket().enEstado(EstadoTicket.EN_CURSO).conAgente(java.util.UUID.randomUUID()).construir();

        assertThat(ticket.eventosPendientes()).isEmpty();
        assertThat(ticket.version()).isZero();
    }

    @Test
    void reconstituirExigePrioridadYSlaJuntos() {
        assertThatThrownBy(() -> Ticket.reconstituir(TicketId.nuevo(), "Titulo", "Descripcion larga",
                java.util.UUID.randomUUID(), null, EstadoTicket.CLASIFICADO, Prioridad.P1, null, null, false, null,
                AHORA, AHORA, 0L))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void extraerEventosLosDevuelveYVaciaLaLista() {
        Ticket ticket = unTicket().crear();

        assertThat(ticket.extraerEventos()).hasSize(1);
        assertThat(ticket.extraerEventos()).isEmpty();
        assertThat(ticket.eventosPendientes()).isEmpty();
    }

    @Test
    void dosTicketsSonIgualesSiTienenElMismoId() {
        Ticket ticket = unTicket().crear();
        Ticket mismoId = Ticket.reconstituir(ticket.id(), "Otro titulo", "Otra descripcion", ticket.solicitanteId(),
                null, EstadoTicket.NUEVO, null, null, null, false, null, AHORA, AHORA, 3L);

        assertThat(ticket).isEqualTo(mismoId).hasSameHashCodeAs(mismoId);
        assertThat(ticket).isNotEqualTo(unTicket().crear());
        assertThat(ticket.toString()).contains(ticket.id().toString());
    }
}
