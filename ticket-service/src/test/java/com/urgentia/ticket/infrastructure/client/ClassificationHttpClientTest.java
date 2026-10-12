package com.urgentia.ticket.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.application.exception.ClasificacionNoDisponibleException;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Prioridad;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.infrastructure.RespuestasIa;
import com.urgentia.ticket.infrastructure.ServidorFalso;
import com.urgentia.ticket.infrastructure.ServidorFalso.Respuesta;
import com.urgentia.ticket.infrastructure.rest.CorrelationIdFilter;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

/** El cliente de la IA: timeout, errores y reenvio del correlationId. */
class ClassificationHttpClientTest {

    private static final String RUTA = "/api/clasificaciones";
    private static final TicketId TICKET = TicketId.nuevo();

    private final ServidorFalso ia = new ServidorFalso();

    @AfterEach
    void apagar() {
        ia.detener();
        MDC.clear();
    }

    @Test
    void devuelveLaClasificacionYReenviaElCorrelationId() {
        ia.responder(RUTA, Respuesta.json(200, RespuestasIa.laDeLaDemo()));
        MDC.put(CorrelationIdFilter.MDC_CLAVE, "cid-ia");

        Clasificacion c = cliente(ia.url(), 1000).clasificar(TICKET, "Titulo de prueba", "Descripcion de prueba");

        assertThat(c.requiereEscalamiento()).isTrue();
        assertThat(c.urgencia().name()).isEqualTo("ALTA");
        assertThat(Prioridad.P1.horasSla()).isEqualTo(1);
        assertThat(ia.pedidos()).hasSize(1);
        assertThat(ia.pedidos().get(0).metodo()).isEqualTo("POST");
        assertThat(ia.pedidos().get(0).header("X-Correlation-Id")).isEqualTo("cid-ia");
        assertThat(ia.pedidos().get(0).cuerpo()).contains("\"ticketId\":\"" + TICKET + "\"");
    }

    @Test
    void unErrorDelServidorEsIaNoDisponible() {
        ia.responder(RUTA, Respuesta.json(502, "{\"codigo\":\"LLM_RESPUESTA_INVALIDA\"}"));

        assertThatThrownBy(() -> cliente(ia.url(), 1000).clasificar(TICKET, "Titulo", "Descripcion larga"))
                .isInstanceOf(ClasificacionNoDisponibleException.class);
    }

    @Test
    void unaRespuestaLentaCortaPorTimeout() {
        ia.responder(RUTA, Respuesta.json(200, RespuestasIa.laDeLaDemo()).conDemora(Duration.ofSeconds(3)));

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> cliente(ia.url(), 500).clasificar(TICKET, "Titulo", "Descripcion larga"))
                .isInstanceOf(ClasificacionNoDisponibleException.class);
        assertThat((System.nanoTime() - inicio) / 1_000_000).isLessThan(2500);
    }

    @Test
    void siElServicioNoExisteEsIaNoDisponible() {
        assertThatThrownBy(() -> cliente("http://127.0.0.1:1", 500).clasificar(TICKET, "Titulo", "Descripcion larga"))
                .isInstanceOf(ClasificacionNoDisponibleException.class);
    }

    @Test
    void unaRespuestaQueNoCumpleElContratoEsIaNoDisponible() {
        ia.responder(RUTA, Respuesta.json(200, "{\"categoria\":\"INCIDENTE\"}"));

        assertThatThrownBy(() -> cliente(ia.url(), 1000).clasificar(TICKET, "Titulo", "Descripcion larga"))
                .isInstanceOf(ClasificacionNoDisponibleException.class)
                .hasMessageContaining("invalida");
    }

    private static ClassificationHttpClient cliente(String url, long timeoutMs) {
        return new ClassificationHttpClient(RestClient.builder(), url, timeoutMs, Clock.systemUTC());
    }
}
