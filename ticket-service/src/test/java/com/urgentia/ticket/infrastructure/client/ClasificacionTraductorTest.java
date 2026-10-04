package com.urgentia.ticket.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.ModuloAfectado;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.domain.model.Urgencia;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** El ACL acepta solo lo que cumple el contrato de classification-service. */
class ClasificacionTraductorTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T14:03:20Z");
    private static final TicketId TICKET = TicketId.de("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private final ClasificacionTraductor traductor = new ClasificacionTraductor(Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void traduceUnaRespuestaValida() {
        Clasificacion c = traductor.traducir(valida(), TICKET);

        assertThat(c.categoria()).isEqualTo(Categoria.INCIDENTE);
        assertThat(c.urgencia()).isEqualTo(Urgencia.ALTA);
        assertThat(c.impacto()).isEqualTo(Impacto.ALTO);
        assertThat(c.moduloAfectado()).isEqualTo(ModuloAfectado.AUTENTICACION);
        assertThat(c.requiereEscalamiento()).isTrue();
        assertThat(c.confianza()).isEqualTo(0.93);
        assertThat(c.justificacion()).isEqualTo("Caida total");
        assertThat(c.proveedor()).isEqualTo("mock");
        assertThat(c.fecha()).isEqualTo(Instant.parse("2026-10-05T14:03:11Z"));
    }

    @Test
    void aceptaQueNoVengaElTicketIdYUsaLaHoraActualSiNoVieneLaFecha() {
        ClasificacionRespuesta r = new ClasificacionRespuesta(null, "BUG", "MEDIA", "BAJO", "FACTURACION", false,
                0.85, null, "mock", null);

        Clasificacion c = traductor.traducir(r, TICKET);

        assertThat(c.fecha()).isEqualTo(AHORA);
        assertThat(c.justificacion()).isNull();
    }

    @Test
    void recortaLaFechaASegundos() {
        ClasificacionRespuesta r = new ClasificacionRespuesta(null, "BUG", "MEDIA", "BAJO", "FACTURACION", false,
                0.85, "x", "mock", "2026-10-05T14:03:11.987Z");

        assertThat(traductor.traducir(r, TICKET).fecha()).isEqualTo(Instant.parse("2026-10-05T14:03:11Z"));
    }

    @Test
    void recortaLaJustificacionA300Caracteres() {
        ClasificacionRespuesta r = new ClasificacionRespuesta(null, "BUG", "MEDIA", "BAJO", "FACTURACION", false,
                0.85, "j".repeat(500), "mock", null);

        assertThat(traductor.traducir(r, TICKET).justificacion()).hasSize(300);
    }

    @Test
    void rechazaRespuestasQueNoCumplenElContrato() {
        invalida(null, "vacia");
        invalida(new ClasificacionRespuesta("otro-ticket", "BUG", "MEDIA", "BAJO", "OTRO", false, 0.8, "x", "m", null),
                "otro ticket");
        invalida(new ClasificacionRespuesta(null, "URGENTE", "MEDIA", "BAJO", "OTRO", false, 0.8, "x", "m", null),
                "categoria");
        invalida(new ClasificacionRespuesta(null, "BUG", "altisima", "BAJO", "OTRO", false, 0.8, "x", "m", null),
                "urgencia");
        invalida(new ClasificacionRespuesta(null, "BUG", "MEDIA", null, "OTRO", false, 0.8, "x", "m", null),
                "impacto");
        invalida(new ClasificacionRespuesta(null, "BUG", "MEDIA", "BAJO", "COCINA", false, 0.8, "x", "m", null),
                "moduloAfectado");
        invalida(new ClasificacionRespuesta(null, "BUG", "MEDIA", "BAJO", "OTRO", null, 0.8, "x", "m", null),
                "requiereEscalamiento");
        invalida(new ClasificacionRespuesta(null, "BUG", "MEDIA", "BAJO", "OTRO", false, null, "x", "m", null),
                "confianza");
        invalida(new ClasificacionRespuesta(null, "BUG", "MEDIA", "BAJO", "OTRO", false, 1.5, "x", "m", null),
                "confianza");
        invalida(new ClasificacionRespuesta(null, "BUG", "MEDIA", "BAJO", "OTRO", false, 0.8, "x", "m", "ayer"),
                "fecha");
    }

    private void invalida(ClasificacionRespuesta respuesta, String motivo) {
        assertThatThrownBy(() -> traductor.traducir(respuesta, TICKET))
                .as(motivo)
                .isInstanceOf(RespuestaInvalidaException.class);
    }

    private static ClasificacionRespuesta valida() {
        return new ClasificacionRespuesta(TICKET.toString(), "INCIDENTE", "ALTA", "ALTO", "AUTENTICACION", true, 0.93,
                "Caida total", "mock", "2026-10-05T14:03:11Z");
    }
}
