package com.urgentia.ticket.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.Prioridad;
import com.urgentia.ticket.domain.model.Urgencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MatrizItilStrategyTest {

    private final PrioridadStrategy matriz = new MatrizItilStrategy();

    @DisplayName("Las 9 combinaciones de la matriz de la seccion 8.1")
    @ParameterizedTest(name = "urgencia {0} + impacto {1} = {2}")
    @CsvSource({
            "ALTA,  ALTO,  P1",
            "ALTA,  MEDIO, P2",
            "ALTA,  BAJO,  P3",
            "MEDIA, ALTO,  P2",
            "MEDIA, MEDIO, P3",
            "MEDIA, BAJO,  P4",
            "BAJA,  ALTO,  P3",
            "BAJA,  MEDIO, P4",
            "BAJA,  BAJO,  P4"
    })
    void calculaLaPrioridadSegunLaMatriz(Urgencia urgencia, Impacto impacto, Prioridad esperada) {
        assertThat(matriz.calcular(urgencia, impacto)).isEqualTo(esperada);
    }

    @Test
    void sinUrgenciaOImpactoNoSePuedeCalcular() {
        assertThatThrownBy(() -> matriz.calcular(null, Impacto.ALTO)).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> matriz.calcular(Urgencia.ALTA, null)).isInstanceOf(ReglaDeNegocioException.class);
    }
}
