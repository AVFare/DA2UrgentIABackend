package com.urgentia.ticket.domain.model;

import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import java.util.Objects;
import java.util.UUID;

/** Identidad del ticket (Value Object). UUID v4. */
public record TicketId(UUID valor) {

    public TicketId {
        Objects.requireNonNull(valor, "El id del ticket es obligatorio");
    }

    public static TicketId nuevo() {
        return new TicketId(UUID.randomUUID());
    }

    public static TicketId de(UUID valor) {
        return new TicketId(valor);
    }

    /** Convierte un texto a TicketId. Lanza ReglaDeNegocioException si no es un UUID. */
    public static TicketId de(String valor) {
        try {
            return new TicketId(UUID.fromString(Objects.requireNonNull(valor, "El id del ticket es obligatorio")));
        } catch (IllegalArgumentException e) {
            throw new ReglaDeNegocioException("El id del ticket no es un UUID valido: " + valor);
        }
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
