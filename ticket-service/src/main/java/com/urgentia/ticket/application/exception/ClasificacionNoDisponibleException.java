package com.urgentia.ticket.application.exception;

/**
 * La IA no pudo clasificar: error, timeout, 5xx o respuesta que no cumple el contrato.
 * Al crear, el ticket queda PENDIENTE_CLASIFICACION; al reclasificar, se responde 503.
 */
public class ClasificacionNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ClasificacionNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public ClasificacionNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
