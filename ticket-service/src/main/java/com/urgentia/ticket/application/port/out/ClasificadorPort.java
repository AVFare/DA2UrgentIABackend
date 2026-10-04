package com.urgentia.ticket.application.port.out;

import com.urgentia.ticket.application.exception.ClasificacionNoDisponibleException;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.TicketId;

/**
 * Puerto de salida hacia la IA. La aplicacion pide una clasificacion y recibe un
 * Value Object del dominio; el adaptador (ACL) traduce el contrato de classification-service.
 */
public interface ClasificadorPort {

    /**
     * @throws ClasificacionNoDisponibleException si hubo error, timeout, 5xx o una respuesta invalida
     */
    Clasificacion clasificar(TicketId ticketId, String titulo, String descripcion);
}
