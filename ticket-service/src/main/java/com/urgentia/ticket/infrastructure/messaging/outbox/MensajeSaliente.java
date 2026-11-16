package com.urgentia.ticket.infrastructure.messaging.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un mensaje para RabbitMQ tal como se guarda en el Outbox: el sobre ya serializado en JSON
 * (cuerpo), a que exchange va y con que routing key. Las propiedades AMQP (message_id,
 * correlation_id, type, timestamp) salen de estos mismos datos al publicar.
 */
public record MensajeSaliente(
        UUID eventId,
        String eventType,
        String exchange,
        String routingKey,
        String correlationId,
        UUID ticketId,
        String cuerpo,
        Instant ocurridoEn) {

    public MensajeSaliente {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(routingKey, "routingKey");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(cuerpo, "cuerpo");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
    }
}
