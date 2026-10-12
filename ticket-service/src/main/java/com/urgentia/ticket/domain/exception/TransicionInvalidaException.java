package com.urgentia.ticket.domain.exception;

import com.urgentia.ticket.domain.model.EstadoTicket;

/** La tabla de transiciones (seccion 8.4) no permite el cambio pedido. */
public class TransicionInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final EstadoTicket desde;
    private final EstadoTicket hacia;

    public TransicionInvalidaException(EstadoTicket desde, EstadoTicket hacia) {
        this(desde, hacia, "No se puede pasar de " + desde + " a " + hacia);
    }

    public TransicionInvalidaException(EstadoTicket desde, EstadoTicket hacia, String mensaje) {
        super(mensaje);
        this.desde = desde;
        this.hacia = hacia;
    }

    public EstadoTicket desde() {
        return desde;
    }

    public EstadoTicket hacia() {
        return hacia;
    }
}
