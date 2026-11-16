package com.urgentia.ticket.infrastructure.messaging.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica un mensaje del Outbox en RabbitMQ siguiendo las reglas para quien publica de
 * contracts/messaging/README.md: propiedades AMQP, mensaje persistente, mandatory y
 * confirmacion del broker (publisher confirms). Recien con el ack del broker el mensaje
 * se da por enviado.
 */
@Component
public class EnviadorRabbit implements EnviadorDeMensajes {

    static final String APP_ID = "ticket-service";

    private final RabbitTemplate rabbit;
    private final Duration esperaConfirmacion;

    public EnviadorRabbit(RabbitTemplate rabbit,
                          @Value("${outbox.publicador.timeout-confirmacion-ms:5000}") long timeoutMs) {
        this.rabbit = rabbit;
        this.esperaConfirmacion = Duration.ofMillis(timeoutMs);
    }

    @Override
    public void enviar(MensajeSaliente mensaje) {
        CorrelationData confirmacion = new CorrelationData(mensaje.eventId().toString());
        rabbit.send(mensaje.exchange(), mensaje.routingKey(), aMensajeAmqp(mensaje), confirmacion);

        CorrelationData.Confirm resultado = esperar(confirmacion);
        // Con mandatory, si ninguna cola recibe el mensaje el broker lo devuelve (y despues igual hace ack).
        ReturnedMessage devuelto = confirmacion.getReturned();
        if (devuelto != null) {
            throw new PublicacionFallidaException("Ninguna cola recibio el mensaje en " + mensaje.exchange()
                    + " con routing key " + mensaje.routingKey() + ": " + devuelto.getReplyText());
        }
        if (!resultado.isAck()) {
            throw new PublicacionFallidaException("El broker rechazo el mensaje: " + resultado.getReason());
        }
    }

    static Message aMensajeAmqp(MensajeSaliente mensaje) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setContentEncoding(StandardCharsets.UTF_8.name().toLowerCase());
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        propiedades.setMessageId(mensaje.eventId().toString());
        propiedades.setCorrelationId(mensaje.correlationId());
        propiedades.setType(mensaje.eventType());
        propiedades.setAppId(APP_ID);
        // AMQP guarda el timestamp en segundos Unix.
        propiedades.setTimestamp(Date.from(mensaje.ocurridoEn()));
        return new Message(mensaje.cuerpo().getBytes(StandardCharsets.UTF_8), propiedades);
    }

    private CorrelationData.Confirm esperar(CorrelationData confirmacion) {
        try {
            return confirmacion.getFuture().get(esperaConfirmacion.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new PublicacionFallidaException(
                    "El broker no confirmo el mensaje en " + esperaConfirmacion.toMillis() + " ms");
        } catch (ExecutionException e) {
            throw new PublicacionFallidaException("Fallo la confirmacion del broker", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublicacionFallidaException("Se interrumpio la espera de la confirmacion", e);
        }
    }
}
