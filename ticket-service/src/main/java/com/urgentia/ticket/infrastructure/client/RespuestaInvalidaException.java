package com.urgentia.ticket.infrastructure.client;

/** La respuesta de la IA no cumple el contrato de classification-service. */
public class RespuestaInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RespuestaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
