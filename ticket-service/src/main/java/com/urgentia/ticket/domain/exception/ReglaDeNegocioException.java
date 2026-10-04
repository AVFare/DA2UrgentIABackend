package com.urgentia.ticket.domain.exception;

/** Se viola una invariante del ticket que no es un cambio de estado (ej.: resolver sin agente). */
public class ReglaDeNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
