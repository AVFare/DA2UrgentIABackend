package com.urgentia.ticket.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urgentia.ticket.application.port.out.SolicitudDeClasificacionPort;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.infrastructure.messaging.outbox.MensajeSaliente;
import com.urgentia.ticket.infrastructure.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Adapter del puerto SolicitudDeClasificacionPort: guarda un ClasificacionSolicitada en el outbox,
 * en la misma transaccion que el ticket, para el exchange urgentia.clasificacion. Lo publica
 * PublicadorDelOutbox y lo consume el worker de classification-service.
 */
@Component
public class OutboxSolicitudDeClasificacionAdapter implements SolicitudDeClasificacionPort {

    private final Outbox outbox;
    private final ObjectMapper json;
    private final Clock reloj;

    public OutboxSolicitudDeClasificacionAdapter(Outbox outbox, ObjectMapper json, Clock reloj) {
        this.outbox = outbox;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    public void solicitar(TicketId ticketId, String titulo, String descripcion) {
        String correlationId = CorrelationIdActual.obtener();
        SobreClasificacionSolicitada sobre = SobreClasificacionSolicitada.nuevo(ticketId.valor(), titulo, descripcion,
                Instant.now(reloj), correlationId);
        outbox.guardar(new MensajeSaliente(sobre.eventId(), sobre.eventType(), TopologiaRabbit.EXCHANGE_CLASIFICACION,
                TopologiaRabbit.CLASIFICACION_SOLICITADA, correlationId, ticketId.valor(), aJson(sobre),
                sobre.occurredAt()));
    }

    private String aJson(SobreClasificacionSolicitada sobre) {
        try {
            return json.writeValueAsString(sobre);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la solicitud " + sobre.eventId(), e);
        }
    }
}
