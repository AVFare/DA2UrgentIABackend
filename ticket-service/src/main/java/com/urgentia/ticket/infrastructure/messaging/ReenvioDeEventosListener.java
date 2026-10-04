package com.urgentia.ticket.infrastructure.messaging;

import com.urgentia.ticket.domain.event.DomainEvent;
import com.urgentia.ticket.infrastructure.rest.CorrelationIdFilter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: escucha los eventos de dominio y, recien cuando la transaccion confirma
 * (AFTER_COMMIT), los arma con el sobre estandar y los encola para enviarlos por HTTP.
 * Si la transaccion se deshace, el evento no sale. No bloquea la respuesta al cliente.
 */
@Component
public class ReenvioDeEventosListener {

    private static final Logger log = LoggerFactory.getLogger(ReenvioDeEventosListener.class);

    private final HttpEventSender enviador;

    public ReenvioDeEventosListener(HttpEventSender enviador) {
        this.enviador = enviador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alConfirmarse(DomainEvent evento) {
        try {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_CLAVE);
            enviador.enviar(EventoEnvelope.desde(evento,
                    correlationId != null ? correlationId : UUID.randomUUID().toString()));
        } catch (RuntimeException e) {
            // Nunca romper la respuesta al cliente por un problema al encolar el evento.
            log.error("No se pudo encolar el evento {} ({}): {}", evento.eventId(), evento.tipo(), e.getMessage(), e);
        }
    }
}
