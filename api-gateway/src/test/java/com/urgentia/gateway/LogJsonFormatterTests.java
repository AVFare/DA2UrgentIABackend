package com.urgentia.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urgentia.gateway.config.LogJsonFormatter;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.env.MockEnvironment;

class LogJsonFormatterTests {

    private final LogJsonFormatter formatter = new LogJsonFormatter(
            new MockEnvironment().withProperty("spring.application.name", "api-gateway"));
    private final Logger logger = new LoggerContext().getLogger("prueba");

    @Test
    void escribeUnaLineaJsonConLosCamposDelContexto() throws Exception {
        LoggingEvent evento = evento("GET {}", "/api/tickets");
        evento.addKeyValuePair(new KeyValuePair("correlationId", "abc-123"));

        String linea = formatter.format(evento);

        assertThat(linea).endsWith("\n");
        assertThat(linea.trim()).doesNotContain("\n");
        JsonNode json = new ObjectMapper().readTree(linea);
        assertThat(json.get("timestamp").asText()).endsWith("Z");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("service").asText()).isEqualTo("api-gateway");
        assertThat(json.get("correlationId").asText()).isEqualTo("abc-123");
        assertThat(json.get("message").asText()).isEqualTo("GET /api/tickets");
    }

    @Test
    void sinCorrelationIdOmiteElCampo() throws Exception {
        String linea = formatter.format(evento("Arranco el gateway"));

        JsonNode json = new ObjectMapper().readTree(linea);
        assertThat(json.has("correlationId")).isFalse();
        assertThat(json.get("message").asText()).isEqualTo("Arranco el gateway");
    }

    private LoggingEvent evento(String mensaje, Object... argumentos) {
        return new LoggingEvent(getClass().getName(), logger, Level.INFO, mensaje, null, argumentos);
    }
}
