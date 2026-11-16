package com.urgentia.ticket.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

/** Propiedades AMQP de las reglas para quien publica (contracts/messaging/README.md). */
class EnviadorRabbitTest {

    @Test
    void armaElMensajeConLasPropiedadesDelContrato() {
        UUID eventId = UUID.fromString("3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
        MensajeSaliente saliente = new MensajeSaliente(eventId, "TicketEscalado", "urgentia.eventos",
                "TicketEscalado", "cid-1", UUID.randomUUID(), "{\"titulo\":\"Producción caída\"}",
                Instant.parse("2026-10-05T14:03:12Z"));

        Message mensaje = EnviadorRabbit.aMensajeAmqp(saliente);

        MessageProperties propiedades = mensaje.getMessageProperties();
        assertThat(propiedades.getContentType()).isEqualTo("application/json");
        assertThat(propiedades.getContentEncoding()).isEqualTo("utf-8");
        assertThat(propiedades.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(propiedades.getMessageId()).isEqualTo(eventId.toString());
        assertThat(propiedades.getCorrelationId()).isEqualTo("cid-1");
        assertThat(propiedades.getType()).isEqualTo("TicketEscalado");
        assertThat(propiedades.getAppId()).isEqualTo("ticket-service");
        assertThat(propiedades.getTimestamp().toInstant()).isEqualTo(Instant.parse("2026-10-05T14:03:12Z"));
        assertThat(new String(mensaje.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"titulo\":\"Producción caída\"}");
    }
}
