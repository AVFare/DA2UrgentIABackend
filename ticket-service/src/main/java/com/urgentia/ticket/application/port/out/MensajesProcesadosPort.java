package com.urgentia.ticket.application.port.out;

import java.util.UUID;

/**
 * Registro de los mensajes ya procesados, para que el consumidor sea idempotente:
 * RabbitMQ entrega "al menos una vez", asi que un mismo mensaje puede llegar dos veces.
 */
public interface MensajesProcesadosPort {

    boolean yaProcesado(UUID mensajeId);

    /** Se llama en la misma transaccion que los cambios que produjo el mensaje. */
    void registrar(UUID mensajeId, String tipo);
}
