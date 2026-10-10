package com.urgentia.user.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.util.List;
import java.util.Map;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * Escribe cada log como una linea JSON con los campos que pide el contexto:
 * timestamp, level, service, correlationId y message (mismo formato que los demas servicios).
 * El correlationId se toma del MDC, donde lo deja CorrelationIdFilter durante el pedido.
 * Se activa en application.yml con logging.structured.format.console.
 */
public class LogJsonFormatter implements StructuredLogFormatter<ILoggingEvent> {

    public static final String CORRELATION_ID = "correlationId";

    private final JsonWriter<ILoggingEvent> writer;

    public LogJsonFormatter(Environment environment) {
        String servicio = environment.getProperty("spring.application.name", "user-service");
        this.writer = JsonWriter.<ILoggingEvent>of(members -> {
            members.add("timestamp", ILoggingEvent::getInstant);
            members.add("level", ILoggingEvent::getLevel);
            members.add("service", servicio);
            members.add(CORRELATION_ID, LogJsonFormatter::correlationId).whenNotNull();
            members.add("message", ILoggingEvent::getFormattedMessage);
            members.add("stackTrace", LogJsonFormatter::stackTrace).whenNotNull();
        }).withNewLineAtEnd();
    }

    @Override
    public String format(ILoggingEvent event) {
        return writer.writeToString(event);
    }

    private static String correlationId(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null && mdc.get(CORRELATION_ID) != null) {
            return mdc.get(CORRELATION_ID);
        }
        List<KeyValuePair> datos = event.getKeyValuePairs();
        if (datos == null) {
            return null;
        }
        for (KeyValuePair dato : datos) {
            if (CORRELATION_ID.equals(dato.key) && dato.value != null) {
                return dato.value.toString();
            }
        }
        return null;
    }

    private static String stackTrace(ILoggingEvent event) {
        return event.getThrowableProxy() == null ? null : ThrowableProxyUtil.asString(event.getThrowableProxy());
    }
}
