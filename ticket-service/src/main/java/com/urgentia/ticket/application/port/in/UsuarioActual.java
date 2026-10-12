package com.urgentia.ticket.application.port.in;

import com.urgentia.ticket.application.exception.DatosInvalidosException;
import java.util.UUID;

/**
 * Quien hace el pedido, segun los headers X-User-Id y X-User-Rol del gateway.
 * Los dos pueden faltar en una llamada interna (sin gateway): en ese caso no se restringe nada.
 */
public record UsuarioActual(UUID id, Rol rol) {

    public UsuarioActual {
        if (rol == Rol.SOLICITANTE && id == null) {
            throw new DatosInvalidosException("X-User-Id", "Falta el id del usuario solicitante");
        }
    }

    public static UsuarioActual interno() {
        return new UsuarioActual(null, null);
    }

    /** Un SOLICITANTE solo puede ver sus propios tickets (seccion 6.1). */
    public boolean soloVeLosSuyos() {
        return rol == Rol.SOLICITANTE;
    }
}
