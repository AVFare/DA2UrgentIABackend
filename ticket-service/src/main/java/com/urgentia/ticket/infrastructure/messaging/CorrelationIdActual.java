package com.urgentia.ticket.infrastructure.messaging;

import com.urgentia.ticket.infrastructure.rest.CorrelationIdFilter;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * El correlationId del pedido o del mensaje que se esta procesando (lo deja en el MDC el
 * CorrelationIdFilter o el consumidor). Si no hay ninguno, se genera uno nuevo.
 */
final class CorrelationIdActual {

    private CorrelationIdActual() {
    }

    static String obtener() {
        String actual = MDC.get(CorrelationIdFilter.MDC_CLAVE);
        return actual == null || actual.isBlank() ? UUID.randomUUID().toString() : actual;
    }
}
