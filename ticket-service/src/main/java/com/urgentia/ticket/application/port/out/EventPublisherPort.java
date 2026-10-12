package com.urgentia.ticket.application.port.out;

import com.urgentia.ticket.domain.event.DomainEvent;
import java.util.List;

/**
 * Puerto de salida para publicar eventos de dominio. La aplicacion no sabe como viajan
 * (hoy HTTP despues del commit; en la parte 2, un broker): cambiar el transporte no la toca.
 */
public interface EventPublisherPort {

    /** Publica los eventos en el orden recibido. */
    void publicar(List<DomainEvent> eventos);
}
