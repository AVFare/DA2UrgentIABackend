package com.urgentia.ticket.domain.model;

import static com.urgentia.ticket.domain.ClasificacionBuilder.laDeLaDemo;
import static com.urgentia.ticket.domain.TicketBuilder.AGENTE;
import static com.urgentia.ticket.domain.TicketBuilder.AHORA;
import static com.urgentia.ticket.domain.TicketBuilder.unTicket;
import static com.urgentia.ticket.domain.model.EstadoTicket.ASIGNADO;
import static com.urgentia.ticket.domain.model.EstadoTicket.CERRADO;
import static com.urgentia.ticket.domain.model.EstadoTicket.CLASIFICADO;
import static com.urgentia.ticket.domain.model.EstadoTicket.EN_CURSO;
import static com.urgentia.ticket.domain.model.EstadoTicket.ESCALADO;
import static com.urgentia.ticket.domain.model.EstadoTicket.RESUELTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.domain.event.TicketEscalado;
import com.urgentia.ticket.domain.event.TicketEstadoCambiado;
import com.urgentia.ticket.domain.event.TicketResuelto;
import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import com.urgentia.ticket.domain.exception.TransicionInvalidaException;
import com.urgentia.ticket.domain.service.MatrizItilStrategy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TicketTransicionesTest {

    private static final Instant DESPUES = AHORA.plusSeconds(300);
    private static final String MOTIVO = "El cliente reporta pérdida de datos";

    /** Cambios que se pueden pedir con PATCH /estado (seccion 8.4). Todo lo demas es invalido. */
    private static final Map<EstadoTicket, Set<EstadoTicket>> MANUALES = Map.of(
            ASIGNADO, Set.of(EN_CURSO, ESCALADO),
            EN_CURSO, Set.of(RESUELTO, ESCALADO),
            ESCALADO, Set.of(EN_CURSO),
            RESUELTO, Set.of(CERRADO, EN_CURSO));

    static Stream<Arguments> todosLosPares() {
        List<Arguments> pares = new ArrayList<>();
        for (EstadoTicket desde : EstadoTicket.values()) {
            for (EstadoTicket hacia : EstadoTicket.values()) {
                boolean permitida = MANUALES.getOrDefault(desde, Set.of()).contains(hacia);
                pares.add(Arguments.of(desde, hacia, permitida));
            }
        }
        return pares.stream();
    }

    @ParameterizedTest(name = "{0} -> {1}: permitida = {2}")
    @MethodSource("todosLosPares")
    void respetaLaTablaDeTransicionesManuales(EstadoTicket desde, EstadoTicket hacia, boolean permitida) {
        Ticket ticket = unTicket().enEstado(desde).conAgente(AGENTE).construir();

        if (permitida) {
            ticket.cambiarEstado(hacia, MOTIVO, DESPUES);
            assertThat(ticket.estado()).isEqualTo(hacia);
            assertThat(ticket.fechaActualizacion()).isEqualTo(DESPUES);
            assertThat(ticket.eventosPendientes()).first().isInstanceOf(TicketEstadoCambiado.class);
            assertThat(((TicketEstadoCambiado) ticket.eventosPendientes().get(0)).estadoAnterior()).isEqualTo(desde);
        } else {
            assertThatThrownBy(() -> ticket.cambiarEstado(hacia, MOTIVO, DESPUES))
                    .isInstanceOf(TransicionInvalidaException.class);
            assertThat(ticket.estado()).isEqualTo(desde);
            assertThat(ticket.fechaActualizacion()).isEqualTo(AHORA);
            assertThat(ticket.eventosPendientes()).isEmpty();
        }
    }

    @Test
    void elMensajeDeLaTransicionInvalidaDiceDesdeDondeYHaciaDonde() {
        Ticket cerrado = unTicket().enEstado(CERRADO).conAgente(AGENTE).construir();

        assertThatThrownBy(() -> cerrado.cambiarEstado(EN_CURSO, null, DESPUES))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessage("No se puede pasar de CERRADO a EN_CURSO");
    }

    @Test
    void desdeClasificadoNoSeEscalaAMano() {
        Ticket clasificado = unTicket().enEstado(CLASIFICADO).construir();

        assertThatThrownBy(() -> clasificado.escalar(MOTIVO, DESPUES)).isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void pasarAAsignadoPorCambioDeEstadoExplicaQueHayQueAsignar() {
        Ticket clasificado = unTicket().enEstado(CLASIFICADO).construir();

        assertThatThrownBy(() -> clasificado.cambiarEstado(ASIGNADO, null, DESPUES))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("asignar");
    }

    @Test
    void escalarAManoExigeMotivo() {
        Ticket enCurso = unTicket().enEstado(EN_CURSO).conAgente(AGENTE).construir();

        assertThatThrownBy(() -> enCurso.cambiarEstado(ESCALADO, null, DESPUES))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("motivo");
        assertThatThrownBy(() -> enCurso.cambiarEstado(ESCALADO, "   ", DESPUES))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThat(enCurso.estado()).isEqualTo(EN_CURSO);
        assertThat(enCurso.eventosPendientes()).isEmpty();
    }

    @Test
    void escalarAManoGuardaElMotivoYEmiteCambioYEscalado() {
        Ticket asignado = unTicket().enEstado(ASIGNADO).conAgente(AGENTE).construir();

        asignado.cambiarEstado(ESCALADO, "  " + MOTIVO + "  ", DESPUES);

        assertThat(asignado.motivoEscalamiento()).isEqualTo(MOTIVO);
        assertThat(asignado.eventosPendientes()).extracting(DomainEvent::tipo)
                .containsExactly("TicketEstadoCambiado", "TicketEscalado");
        TicketEscalado escalado = (TicketEscalado) asignado.eventosPendientes().get(1);
        assertThat(escalado.estadoAnterior()).isEqualTo(ASIGNADO);
        assertThat(escalado.motivo()).isEqualTo(MOTIVO);
    }

    @Test
    void resolverEmiteCambioYResuelto() {
        Ticket enCurso = unTicket().enEstado(EN_CURSO).conAgente(AGENTE).construir();

        enCurso.cambiarEstado(RESUELTO, null, DESPUES);

        assertThat(enCurso.eventosPendientes()).extracting(DomainEvent::tipo)
                .containsExactly("TicketEstadoCambiado", "TicketResuelto");
        assertThat(((TicketResuelto) enCurso.eventosPendientes().get(1)).estadoAnterior()).isEqualTo(EN_CURSO);
    }

    @Test
    void cerrarYReabrirSoloEmitenCambioDeEstado() {
        Ticket resuelto = unTicket().enEstado(RESUELTO).conAgente(AGENTE).construir();
        resuelto.cambiarEstado(EN_CURSO, null, DESPUES);
        assertThat(resuelto.eventosPendientes()).extracting(DomainEvent::tipo).containsExactly("TicketEstadoCambiado");

        Ticket otroResuelto = unTicket().enEstado(RESUELTO).conAgente(AGENTE).construir();
        otroResuelto.cerrar(DESPUES);
        assertThat(otroResuelto.estado()).isEqualTo(CERRADO);
        assertThat(otroResuelto.eventosPendientes()).extracting(DomainEvent::tipo)
                .containsExactly("TicketEstadoCambiado");
    }

    @Test
    void laGuardiaNoPuedeTomarUnEscaladoSinAgenteAsignado() {
        Ticket escalado = unTicket().enEstado(ESCALADO).construir();

        assertThatThrownBy(() -> escalado.cambiarEstado(EN_CURSO, null, DESPUES))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("agente");
        assertThat(escalado.estado()).isEqualTo(ESCALADO);
    }

    @Test
    void resolverYReabrirRequierenAgente() {
        Ticket enCursoSinAgente = unTicket().enEstado(EN_CURSO).construir();
        assertThatThrownBy(() -> enCursoSinAgente.resolver(DESPUES)).isInstanceOf(ReglaDeNegocioException.class);

        Ticket resueltoSinAgente = unTicket().enEstado(RESUELTO).construir();
        assertThatThrownBy(() -> resueltoSinAgente.reabrir(DESPUES)).isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void unaTransicionInvalidaGanaAntesQueLaFaltaDeAgente() {
        Ticket cerrado = unTicket().enEstado(CERRADO).construir();

        assertThatThrownBy(() -> cerrado.resolver(DESPUES)).isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void elDestinoEsObligatorio() {
        Ticket asignado = unTicket().enEstado(ASIGNADO).conAgente(AGENTE).construir();

        assertThatThrownBy(() -> asignado.cambiarEstado(null, null, DESPUES))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void cicloCompletoDelGuionDeLaDemo() {
        Ticket ticket = unTicket().crear();
        ticket.aplicarClasificacion(laDeLaDemo().build(), new MatrizItilStrategy(), AHORA.plusSeconds(1));
        ticket.asignarA(AGENTE, AHORA.plusSeconds(60));
        ticket.cambiarEstado(EN_CURSO, null, AHORA.plusSeconds(120));
        ticket.cambiarEstado(RESUELTO, null, AHORA.plusSeconds(1800));
        ticket.cambiarEstado(CERRADO, null, AHORA.plusSeconds(3600));

        assertThat(ticket.estado()).isEqualTo(CERRADO);
        assertThat(ticket.motivoEscalamiento()).isEqualTo("Prioridad P1");
        assertThat(ticket.eventosPendientes()).extracting(DomainEvent::tipo).containsExactly(
                "TicketCreado", "TicketClasificado", "TicketEscalado",
                "TicketAsignado",
                "TicketEstadoCambiado",
                "TicketEstadoCambiado", "TicketResuelto",
                "TicketEstadoCambiado");
    }

    @Test
    void slaVencidoSoloSiPasoLaFechaYElTicketSigueAbierto() {
        Ticket abierto = unTicket().enEstado(EN_CURSO).conAgente(AGENTE).construir();
        Instant limite = abierto.fechaLimiteSla();

        assertThat(abierto.slaVencido(limite)).isFalse();
        assertThat(abierto.slaVencido(limite.plusSeconds(1))).isTrue();

        Ticket resuelto = unTicket().enEstado(RESUELTO).conAgente(AGENTE).construir();
        assertThat(resuelto.slaVencido(limite.plusSeconds(1))).isFalse();

        Ticket cerrado = unTicket().enEstado(CERRADO).conAgente(AGENTE).construir();
        assertThat(cerrado.slaVencido(limite.plusSeconds(1))).isFalse();

        Ticket sinClasificar = unTicket().crear();
        assertThat(sinClasificar.slaVencido(limite.plusSeconds(1))).isFalse();
    }
}
