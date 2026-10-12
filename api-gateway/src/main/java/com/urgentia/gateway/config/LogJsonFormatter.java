package com.urgentia.gateway.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.util.List;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * Escribe cada log como una linea JSON con los campos que pide el contexto:
 * timestamp, level, service, correlationId y message.
 * Se activa en application.yml con logging.structured.format.console.
 */
public class LogJsonFormatter implements StructuredLogFormatter<ILoggingEvent> {

    public static final String CORRELATION_ID = "correlationId";

    private final JsonWriter<ILoggingEvent> writer;

    public LogJsonFormatter(Environment environment) {
        String servicio = environment.getProperty("spring.application.name", "api-gateway");
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

    /** El correlationId viaja como dato del log (addKeyValue), no en el texto del mensaje. */
    private static String correlationId(ILoggingEvent event) {
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
