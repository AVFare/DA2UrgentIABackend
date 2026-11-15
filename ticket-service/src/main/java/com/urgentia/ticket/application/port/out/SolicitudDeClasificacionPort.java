package com.urgentia.ticket.application.port.out;

import com.urgentia.ticket.domain.model.TicketId;

/**
 * Puerto de salida para pedir la clasificacion por la cola (Entrega 2, seccion 10.5).
 * A diferencia de ClasificadorPort, no espera la respuesta: la IA contesta despues con un
 * ClasificacionGenerada, que entra por AplicarClasificacionUseCase. Se llama dentro de la
 * transaccion del alta, asi el pedido sale solo si el ticket quedo guardado.
 */
public interface SolicitudDeClasificacionPort {

    void solicitar(TicketId ticketId, String titulo, String descripcion);
}
