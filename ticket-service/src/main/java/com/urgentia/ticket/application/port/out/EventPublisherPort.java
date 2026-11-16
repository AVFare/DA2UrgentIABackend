package com.urgentia.ticket.application.port.out;

import com.urgentia.ticket.domain.event.DomainEvent;
import java.util.List;

/**
 * Puerto de salida para publicar eventos de dominio. La aplicacion no sabe como viajan
 * (en la Entrega 1 por HTTP; ahora por RabbitMQ desde el Transactional Outbox): cambiar el
 * transporte no la toca. Se llama dentro de la transaccion del caso de uso.
 */
public interface EventPublisherPort {

    /** Publica los eventos en el orden recibido. */
    void publicar(List<DomainEvent> eventos);
}
