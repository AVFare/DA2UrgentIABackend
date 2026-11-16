package com.urgentia.ticket.infrastructure.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Duration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumidor de la cola tickets.clasificaciones (seccion 10.5): recibe las ClasificacionGenerada
 * que publica el worker de classification-service.
 *
 * <p>Ack manual, despues de procesar: CONFIRMAR hace ack; DESCARTAR, reject sin reencolar (va directo
 * a tickets.clasificaciones.dlq); REINTENTAR, reject con reencolado, y RabbitMQ lo manda a la DLQ
 * despues de 5 reintentos (x-delivery-limit). Antes de reencolar espera un poco, para que los
 * reintentos no se gasten en milisegundos si la base esta caida.
 */
@Component
public class ConsumidorDeClasificaciones {

    private final ManejadorDeClasificaciones manejador;
    private final Duration esperaAntesDeReintentar;

    public ConsumidorDeClasificaciones(ManejadorDeClasificaciones manejador,
                                       @Value("${clasificacion.cola.espera-reintento-ms:1000}") long esperaMs) {
        this.manejador = manejador;
        this.esperaAntesDeReintentar = Duration.ofMillis(esperaMs);
    }

    @RabbitListener(id = "clasificaciones", queues = TopologiaRabbit.COLA_CLASIFICACIONES, ackMode = "MANUAL")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        switch (manejador.manejar(mensaje.getBody())) {
            case CONFIRMAR -> canal.basicAck(etiqueta, false);
            case DESCARTAR -> canal.basicReject(etiqueta, false);
            case REINTENTAR -> {
                esperar();
                canal.basicReject(etiqueta, true);
            }
        }
    }

    private void esperar() {
        try {
            Thread.sleep(esperaAntesDeReintentar.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
