package com.urgentia.ticket.infrastructure.messaging;

import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.domain.event.DomainEvent;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Adapter del puerto EventPublisherPort sobre los eventos de Spring (patron Observer).
 * Solo avisa "paso esto"; quien escucha decide que hacer y cuando
 * (ReenvioDeEventosListener los reenvia por HTTP recien despues del commit).
 * En la parte 2 se cambia este adaptador por uno que publique en el broker.
 */
@Component
public class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher publicador;

    public SpringEventPublisherAdapter(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    @Override
    public void publicar(List<DomainEvent> eventos) {
        eventos.forEach(publicador::publishEvent);
    }
}
