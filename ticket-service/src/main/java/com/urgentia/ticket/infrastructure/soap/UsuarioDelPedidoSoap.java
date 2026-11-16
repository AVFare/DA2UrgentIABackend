package com.urgentia.ticket.infrastructure.soap;

import com.urgentia.ticket.application.exception.DatosInvalidosException;
import com.urgentia.ticket.application.port.in.Rol;
import com.urgentia.ticket.application.port.in.UsuarioActual;
import com.urgentia.ticket.infrastructure.rest.TicketController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;

/**
 * Arma el UsuarioActual a partir de X-User-Id y X-User-Rol, los headers que el gateway pone
 * despues de validar el JWT. Es lo mismo que hace TicketController para el REST: un SOLICITANTE
 * solo ve sus tickets. Sin headers (llamada interna, sin gateway) no se restringe nada.
 */
@Component
public class UsuarioDelPedidoSoap {

    public UsuarioActual actual() {
        TransportContext contexto = TransportContextHolder.getTransportContext();
        if (contexto != null && contexto.getConnection() instanceof HttpServletConnection conexion) {
            HttpServletRequest pedido = conexion.getHttpServletRequest();
            return construir(pedido.getHeader(TicketController.HEADER_USER_ID),
                    pedido.getHeader(TicketController.HEADER_USER_ROL));
        }
        return UsuarioActual.interno();
    }

    static UsuarioActual construir(String usuarioId, String rol) {
        UUID id = null;
        if (usuarioId != null && !usuarioId.isBlank()) {
            try {
                id = UUID.fromString(usuarioId.strip());
            } catch (IllegalArgumentException e) {
                throw new DatosInvalidosException(TicketController.HEADER_USER_ID, "no es un UUID: " + usuarioId);
            }
        }
        Rol rolActual = null;
        if (rol != null && !rol.isBlank()) {
            try {
                rolActual = Rol.valueOf(rol.strip());
            } catch (IllegalArgumentException e) {
                throw new DatosInvalidosException(TicketController.HEADER_USER_ROL, "rol desconocido: " + rol);
            }
        }
        return new UsuarioActual(id, rolActual);
    }
}
