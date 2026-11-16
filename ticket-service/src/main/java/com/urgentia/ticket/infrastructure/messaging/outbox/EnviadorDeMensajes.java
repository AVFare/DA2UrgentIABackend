package com.urgentia.ticket.infrastructure.messaging.outbox;

/**
 * Lo que necesita el publicador del Outbox para mandar un mensaje al broker. Vuelve recien
 * cuando el broker confirmo que lo guardo; si no, lanza una excepcion y el mensaje queda
 * pendiente para el proximo intento. La implementacion real es EnviadorRabbit.
 */
public interface EnviadorDeMensajes {

    /** @throws RuntimeException si el broker no confirmo el mensaje (caido, rechazo o sin cola de destino) */
    void enviar(MensajeSaliente mensaje);
}
