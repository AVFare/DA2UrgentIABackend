package com.urgentia.ticket.application.exception;

import com.urgentia.ticket.domain.model.TicketId;

/** No existe un ticket con ese id (o el solicitante no puede verlo). */
public class TicketNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TicketNoEncontradoException(TicketId id) {
        super("No existe el ticket " + id);
    }
}
