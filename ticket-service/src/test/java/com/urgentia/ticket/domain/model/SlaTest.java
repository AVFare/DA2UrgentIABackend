package com.urgentia.ticket.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlaTest {

    private static final Instant CLASIFICACION = Instant.parse("2026-10-05T14:03:11Z");

    @ParameterizedTest(name = "{0} vence a las {1}")
    @CsvSource({
            "P1, 2026-10-05T15:03:11Z",
            "P2, 2026-10-05T18:03:11Z",
            "P3, 2026-10-05T22:03:11Z",
            "P4, 2026-10-06T14:03:11Z"
    })
    void sumaLasHorasCorridasDeLaPrioridad(Prioridad prioridad, String limite) {
        Sla sla = Sla.calcular(prioridad, CLASIFICACION);

        assertThat(sla.prioridad()).isEqualTo(prioridad);
        assertThat(sla.fechaLimite()).isEqualTo(Instant.parse(limite));
    }

    @Test
    void venceRecienCuandoLaFechaLimiteQuedaAtras() {
        Sla sla = Sla.calcular(Prioridad.P1, CLASIFICACION);
        Instant limite = sla.fechaLimite();

        assertThat(sla.vencidoA(limite.minusSeconds(1))).isFalse();
        assertThat(sla.vencidoA(limite)).isFalse();
        assertThat(sla.vencidoA(limite.plusSeconds(1))).isTrue();
    }

    @Test
    void prioridadYFechaSonObligatorias() {
        assertThatThrownBy(() -> Sla.calcular(null, CLASIFICACION)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Sla.calcular(Prioridad.P1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Sla(Prioridad.P1, null)).isInstanceOf(NullPointerException.class);
    }
}
