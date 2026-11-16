package com.urgentia.ticket.infrastructure.messaging.outbox;

/** El broker no confirmo un mensaje del Outbox: queda pendiente y se reintenta. */
public class PublicacionFallidaException extends RuntimeException {

    public PublicacionFallidaException(String mensaje) {
        super(mensaje);
    }

    public PublicacionFallidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
