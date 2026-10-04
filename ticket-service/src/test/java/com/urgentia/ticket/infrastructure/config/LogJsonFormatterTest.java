package com.urgentia.ticket.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Cada log es una linea JSON con timestamp, level, service, correlationId y message. */
class LogJsonFormatterTest {

    private final LogJsonFormatter formatter =
            new LogJsonFormatter(new MockEnvironment().withProperty("spring.application.name", "ticket-service"));
    private final Logger logger = new LoggerContext().getLogger("prueba");

    @Test
    void escribeLosCamposDelContextoConElCorrelationIdDelMdc() throws Exception {
        LoggingEvent evento = evento("Ticket {} creado", "7c9e6679");
        evento.setMDCPropertyMap(Map.of("correlationId", "cid-log"));

        String linea = formatter.format(evento);

        assertThat(linea).endsWith("\n");
        assertThat(linea.trim()).doesNotContain("\n");
        JsonNode json = new ObjectMapper().readTree(linea);
        assertThat(json.get("timestamp").asText()).isEqualTo("2026-10-05T14:03:11Z");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("service").asText()).isEqualTo("ticket-service");
        assertThat(json.get("correlationId").asText()).isEqualTo("cid-log");
        assertThat(json.get("message").asText()).isEqualTo("Ticket 7c9e6679 creado");
    }

    @Test
    void sinCorrelationIdOmiteElCampo() throws Exception {
        LoggingEvent evento = evento("Arranco ticket-service");
        evento.setMDCPropertyMap(Map.of());

        JsonNode json = new ObjectMapper().readTree(formatter.format(evento));

        assertThat(json.has("correlationId")).isFalse();
        assertThat(json.get("message").asText()).isEqualTo("Arranco ticket-service");
    }

    private LoggingEvent evento(String mensaje, Object... argumentos) {
        LoggingEvent evento = new LoggingEvent(getClass().getName(), logger, Level.INFO, mensaje, null, argumentos);
        evento.setInstant(Instant.parse("2026-10-05T14:03:11Z"));
        return evento;
    }
}
