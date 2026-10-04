package com.urgentia.ticket.infrastructure.client;

/** La respuesta de la IA no cumple el contrato de classification-service. */
class RespuestaInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    RespuestaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
