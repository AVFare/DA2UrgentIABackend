package com.urgentia.ticket.domain.model;

import static com.urgentia.ticket.domain.ClasificacionBuilder.FECHA;
import static com.urgentia.ticket.domain.ClasificacionBuilder.laDeLaDemo;
import static com.urgentia.ticket.domain.ClasificacionBuilder.unaClasificacion;
import static com.urgentia.ticket.domain.TicketBuilder.AHORA;
import static com.urgentia.ticket.domain.TicketBuilder.unTicket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.domain.event.TicketClasificado;
import com.urgentia.ticket.domain.event.TicketCreado;
import com.urgentia.ticket.domain.event.TicketEscalado;
import com.urgentia.ticket.domain.exception.TransicionInvalidaException;
import com.urgentia.ticket.domain.service.MatrizItilStrategy;
import com.urgentia.ticket.domain.service.PrioridadStrategy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TicketClasificacionTest {

    private static final Instant DESPUES = AHORA.plusSeconds(1);
    private final PrioridadStrategy matriz = new MatrizItilStrategy();

    @Test
    void casoNoCriticoQuedaClasificadoConPrioridadYSla() {
        Ticket ticket = unTicket().crear();

        ticket.aplicarClasificacion(unaClasificacion().con(Urgencia.ALTA, Impacto.MEDIO).build(), matriz, DESPUES);

        assertThat(ticket.estado()).isEqualTo(EstadoTicket.CLASIFICADO);
        assertThat(ticket.prioridad()).isEqualTo(Prioridad.P2);
        assertThat(ticket.fechaLimiteSla()).isEqualTo(FECHA.plusSeconds(4 * 3600));
        assertThat(ticket.motivoEscalamiento()).isNull();
        assertThat(ticket.requiereRevisionManual()).isFalse();
        assertThat(ticket.fechaActualizacion()).isEqualTo(DESPUES);
        assertThat(ticket.eventosPendientes()).extracting(DomainEvent::tipo)
                .containsExactly("TicketCreado", "TicketClasificado");
    }

    @Test
    void elDeLaDemoQuedaP1EscaladoPorPrioridad() {
        Ticket ticket = unTicket().crear();

        ticket.aplicarClasificacion(laDeLaDemo().build(), matriz, DESPUES);

        assertThat(ticket.prioridad()).isEqualTo(Prioridad.P1);
        assertThat(ticket.estado()).isEqualTo(EstadoTicket.ESCALADO);
        assertThat(ticket.motivoEscalamiento()).isEqualTo("Prioridad P1");
        assertThat(ticket.fechaLimiteSla()).isEqualTo(Instant.parse("2026-10-05T15:03:11Z"));
        assertThat(ticket.eventosPendientes()).extracting(DomainEvent::tipo)
                .containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");

        TicketEscalado escalado = (TicketEscalado) ticket.eventosPendientes().get(2);
        assertThat(escalado.estadoAnterior()).isEqualTo(EstadoTicket.CLASIFICADO);
        assertThat(escalado.motivo()).isEqualTo("Prioridad P1");
        assertThat(escalado.ticket().estado()).isEqualTo(EstadoTicket.ESCALADO);
        assertThat(escalado.ticket().prioridad()).isEqualTo(Prioridad.P1);
        assertThat(escalado.ticket().categoria()).isEqualTo(Categoria.INCIDENTE);
        assertThat(escalado.ticket().moduloAfectado()).isEqualTo(ModuloAfectado.AUTENTICACION);
        assertThat(escalado.ticket().fechaLimiteSla()).isEqualTo(Instant.parse("2026-10-05T15:03:11Z"));
    }

    @Test
    void elEventoTicketClasificadoLlevaLaFotoAntesDeEscalar() {
        Ticket ticket = unTicket().crear();

        ticket.aplicarClasificacion(laDeLaDemo().build(), matriz, DESPUES);

        TicketClasificado clasificado = (TicketClasificado) ticket.eventosPendientes().get(1);
        assertThat(clasificado.ticket().estado()).isEqualTo(EstadoTicket.CLASIFICADO);
        assertThat(clasificado.ticket().prioridad()).isEqualTo(Prioridad.P1);
    }

    @Test
    void siLaIaLoMarcaCriticoSeFuerzaP1AunqueLaMatrizDigaOtraCosa() {
        Ticket ticket = unTicket().crear();

        ticket.aplicarClasificacion(unaClasificacion().con(Urgencia.MEDIA, Impacto.BAJO).critica().build(),
                matriz, DESPUES);

        assertThat(ticket.prioridad()).isEqualTo(Prioridad.P1);
        assertThat(ticket.estado()).isEqualTo(EstadoTicket.ESCALADO);
        assertThat(ticket.motivoEscalamiento()).isEqualTo("Marcado como crítico por la IA");
        assertThat(ticket.fechaLimiteSla()).isEqualTo(FECHA.plusSeconds(3600));
    }

    @Test
    void conConfianzaBajaPideRevisionManualSinCambiarElEstado() {
        Ticket ticket = unTicket().crear();

        ticket.aplicarClasificacion(unaClasificacion().conConfianza(0.55).build(), matriz, DESPUES);

        assertThat(ticket.requiereRevisionManual()).isTrue();
        assertThat(ticket.estado()).isEqualTo(EstadoTicket.CLASIFICADO);
        assertThat(ticket.prioridad()).isEqualTo(Prioridad.P3);
    }

    @Test
    void conConfianzaBajaYCriticoIgualEscala() {
        Ticket ticket = unTicket().crear();

        ticket.aplicarClasificacion(laDeLaDemo().conConfianza(0.3).build(), matriz, DESPUES);

        assertThat(ticket.requiereRevisionManual()).isTrue();
        assertThat(ticket.estado()).isEqualTo(EstadoTicket.ESCALADO);
    }

    @Test
    void siLaIaFallaQuedaPendienteSinPrioridadNiSla() {
        Ticket ticket = unTicket().crear();

        ticket.marcarPendienteDeClasificacion(DESPUES);

        assertThat(ticket.estado()).isEqualTo(EstadoTicket.PENDIENTE_CLASIFICACION);
        assertThat(ticket.prioridad()).isNull();
        assertThat(ticket.fechaLimiteSla()).isNull();
        assertThat(ticket.clasificacion()).isNull();
        assertThat(ticket.motivoEscalamiento()).isNull();
        assertThat(ticket.eventosPendientes()).hasSize(1).first().isInstanceOf(TicketCreado.class);
    }

    @Test
    void soloUnTicketNuevoPuedeQuedarPendiente() {
        Ticket clasificado = unTicket().enEstado(EstadoTicket.CLASIFICADO).construir();

        assertThatThrownBy(() -> clasificado.marcarPendienteDeClasificacion(DESPUES))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void unTicketPendienteSePuedeReclasificar() {
        Ticket ticket = unTicket().enEstado(EstadoTicket.PENDIENTE_CLASIFICACION).construir();

        ticket.aplicarClasificacion(laDeLaDemo().build(), matriz, DESPUES);

        assertThat(ticket.estado()).isEqualTo(EstadoTicket.ESCALADO);
        assertThat(ticket.eventosPendientes()).extracting(DomainEvent::tipo)
                .containsExactly("TicketClasificado", "TicketEscalado");
    }

    @Test
    void reclasificarUnTicketClasificadoRecalculaPrioridadYSla() {
        Ticket ticket = unTicket().enEstado(EstadoTicket.CLASIFICADO).construir();
        Instant nuevaFecha = FECHA.plusSeconds(600);

        ticket.aplicarClasificacion(unaClasificacion().con(Urgencia.BAJA, Impacto.BAJO).conFecha(nuevaFecha).build(),
                matriz, DESPUES);

        assertThat(ticket.estado()).isEqualTo(EstadoTicket.CLASIFICADO);
        assertThat(ticket.prioridad()).isEqualTo(Prioridad.P4);
        assertThat(ticket.fechaLimiteSla()).isEqualTo(nuevaFecha.plusSeconds(24 * 3600));
        assertThat(ticket.eventosPendientes()).extracting(DomainEvent::tipo).containsExactly("TicketClasificado");
    }

    @Test
    void reclasificarUnTicketClasificadoPuedeEscalarlo() {
        Ticket ticket = unTicket().enEstado(EstadoTicket.CLASIFICADO).construir();

        ticket.aplicarClasificacion(laDeLaDemo().build(), matriz, DESPUES);

        assertThat(ticket.estado()).isEqualTo(EstadoTicket.ESCALADO);
        assertThat(ticket.eventosPendientes()).extracting(DomainEvent::tipo)
                .containsExactly("TicketClasificado", "TicketEscalado");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTicket.class, names = {"ASIGNADO", "EN_CURSO", "ESCALADO", "RESUELTO", "CERRADO"})
    void noSePuedeClasificarFueraDeNuevoPendienteOClasificado(EstadoTicket estado) {
        Ticket ticket = unTicket().enEstado(estado).conAgente(UUID.randomUUID()).construir();

        assertThatThrownBy(() -> ticket.aplicarClasificacion(laDeLaDemo().build(), matriz, DESPUES))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining(estado.name());
        assertThatThrownBy(ticket::verificarQueAdmiteClasificacion)
                .isInstanceOf(TransicionInvalidaException.class);
        assertThat(ticket.eventosPendientes()).isEmpty();
    }

    @Test
    void laPrioridadNuncaLaDecideLaIaSinoLaEstrategia() {
        PrioridadStrategy siempreP4 = (urgencia, impacto) -> Prioridad.P4;
        Ticket ticket = unTicket().crear();

        ticket.aplicarClasificacion(unaClasificacion().con(Urgencia.ALTA, Impacto.ALTO).build(), siempreP4, DESPUES);

        assertThat(ticket.prioridad()).isEqualTo(Prioridad.P4);
        assertThat(ticket.estado()).isEqualTo(EstadoTicket.CLASIFICADO);
    }

    @Test
    void laClasificacionYLaEstrategiaSonObligatorias() {
        Ticket ticket = unTicket().crear();

        assertThatThrownBy(() -> ticket.aplicarClasificacion(null, matriz, DESPUES))
                .isInstanceOf(com.urgentia.ticket.domain.exception.ReglaDeNegocioException.class);
        assertThatThrownBy(() -> ticket.aplicarClasificacion(laDeLaDemo().build(), null, DESPUES))
                .isInstanceOf(com.urgentia.ticket.domain.exception.ReglaDeNegocioException.class);
    }
}
