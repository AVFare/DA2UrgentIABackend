package com.urgentia.ticket.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.infrastructure.messaging.outbox.MensajeSaliente;
import com.urgentia.ticket.infrastructure.messaging.outbox.Outbox;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adapter del puerto EventPublisherPort sobre el Transactional Outbox (ADR-12).
 *
 * <p>No publica nada en el momento: arma el sobre de cada evento (seccion 10.1) y lo guarda en la
 * tabla outbox, dentro de la misma transaccion que el ticket. PublicadorDelOutbox lo lleva despues
 * al exchange urgentia.eventos con routing key = eventType. La aplicacion no se entera del cambio
 * de transporte: sigue llamando al mismo puerto que en la Entrega 1.
 */
@Component
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private final Outbox outbox;
    private final ObjectMapper json;

    public OutboxEventPublisherAdapter(Outbox outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    public void publicar(List<DomainEvent> eventos) {
        String correlationId = CorrelationIdActual.obtener();
        for (DomainEvent evento : eventos) {
            EventoEnvelope sobre = EventoEnvelope.desde(evento, correlationId);
            outbox.guardar(new MensajeSaliente(sobre.eventId(), sobre.eventType(), TopologiaRabbit.EXCHANGE_EVENTOS,
                    sobre.eventType(), correlationId, evento.ticket().ticketId(), aJson(sobre), sobre.occurredAt()));
        }
    }

    private String aJson(EventoEnvelope sobre) {
        try {
            return json.writeValueAsString(sobre);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + sobre.eventId(), e);
        }
    }
}
