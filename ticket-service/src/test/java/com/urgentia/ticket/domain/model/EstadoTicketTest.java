package com.urgentia.ticket.domain.model;

import static com.urgentia.ticket.domain.model.EstadoTicket.ASIGNADO;
import static com.urgentia.ticket.domain.model.EstadoTicket.CERRADO;
import static com.urgentia.ticket.domain.model.EstadoTicket.CLASIFICADO;
import static com.urgentia.ticket.domain.model.EstadoTicket.EN_CURSO;
import static com.urgentia.ticket.domain.model.EstadoTicket.ESCALADO;
import static com.urgentia.ticket.domain.model.EstadoTicket.NUEVO;
import static com.urgentia.ticket.domain.model.EstadoTicket.PENDIENTE_CLASIFICACION;
import static com.urgentia.ticket.domain.model.EstadoTicket.RESUELTO;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EstadoTicketTest {

    @Test
    void laTablaDeTransicionesEsLaDeLaSeccion84() {
        assertThat(NUEVO.siguientesPosibles()).containsExactlyInAnyOrder(CLASIFICADO, PENDIENTE_CLASIFICACION);
        assertThat(PENDIENTE_CLASIFICACION.siguientesPosibles()).containsExactly(CLASIFICADO);
        assertThat(CLASIFICADO.siguientesPosibles()).containsExactlyInAnyOrder(ASIGNADO, ESCALADO);
        assertThat(ASIGNADO.siguientesPosibles()).containsExactlyInAnyOrder(EN_CURSO, ESCALADO);
        assertThat(EN_CURSO.siguientesPosibles()).containsExactlyInAnyOrder(RESUELTO, ESCALADO);
        assertThat(ESCALADO.siguientesPosibles()).containsExactly(EN_CURSO);
        assertThat(RESUELTO.siguientesPosibles()).containsExactlyInAnyOrder(CERRADO, EN_CURSO);
        assertThat(CERRADO.siguientesPosibles()).isEmpty();
    }

    @Test
    void puedePasarAConsultaLaTabla() {
        assertThat(CLASIFICADO.puedePasarA(ASIGNADO)).isTrue();
        assertThat(CERRADO.puedePasarA(EN_CURSO)).isFalse();
        assertThat(NUEVO.puedePasarA(null)).isFalse();
    }

    @Test
    void abiertoEsTodoMenosResueltoYCerrado() {
        for (EstadoTicket estado : EstadoTicket.values()) {
            boolean esperado = estado != RESUELTO && estado != CERRADO;
            assertThat(estado.estaAbierto()).as(estado.name()).isEqualTo(esperado);
        }
    }
}
