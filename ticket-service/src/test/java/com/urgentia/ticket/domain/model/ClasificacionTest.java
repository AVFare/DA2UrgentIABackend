package com.urgentia.ticket.domain.model;

import static com.urgentia.ticket.domain.ClasificacionBuilder.unaClasificacion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClasificacionTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.6, 1.0})
    void aceptaConfianzasEntreCeroYUno(double confianza) {
        assertThat(unaClasificacion().conConfianza(confianza).build().confianza()).isEqualTo(confianza);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NaN})
    void rechazaConfianzasFueraDeRango(double confianza) {
        assertThatThrownBy(() -> unaClasificacion().conConfianza(confianza).build())
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("confianza");
    }

    @Test
    void conConfianzaMenorA06PideRevisionManual() {
        assertThat(unaClasificacion().conConfianza(0.59).build().requiereRevisionManual()).isTrue();
        assertThat(unaClasificacion().conConfianza(0.6).build().requiereRevisionManual()).isFalse();
    }

    @Test
    void losEnumsYLaFechaSonObligatorios() {
        Instant fecha = Instant.parse("2026-10-05T14:03:11Z");
        assertThatThrownBy(() -> new Clasificacion(null, Urgencia.ALTA, Impacto.ALTO, ModuloAfectado.OTRO,
                false, 0.7, "x", "mock", fecha)).hasMessageContaining("categoria");
        assertThatThrownBy(() -> new Clasificacion(Categoria.BUG, null, Impacto.ALTO, ModuloAfectado.OTRO,
                false, 0.7, "x", "mock", fecha)).hasMessageContaining("urgencia");
        assertThatThrownBy(() -> new Clasificacion(Categoria.BUG, Urgencia.ALTA, null, ModuloAfectado.OTRO,
                false, 0.7, "x", "mock", fecha)).hasMessageContaining("impacto");
        assertThatThrownBy(() -> new Clasificacion(Categoria.BUG, Urgencia.ALTA, Impacto.ALTO, null,
                false, 0.7, "x", "mock", fecha)).hasMessageContaining("moduloAfectado");
        assertThatThrownBy(() -> new Clasificacion(Categoria.BUG, Urgencia.ALTA, Impacto.ALTO, ModuloAfectado.OTRO,
                false, 0.7, "x", "mock", null)).hasMessageContaining("fecha");
    }
}
