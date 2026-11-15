package com.urgentia.ticket.application.port.in;

import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.TicketId;
import java.util.Objects;
import java.util.UUID;

/**
 * Aplica la respuesta de la IA que llega por la cola (ClasificacionGenerada, seccion 10.5):
 * con una clasificacion calcula prioridad y SLA y, si corresponde, escala; con un error el
 * ticket sigue en PENDIENTE_CLASIFICACION y se puede reclasificar por REST.
 */
public interface AplicarClasificacionUseCase {

    Resultado aplicar(ClasificacionRecibida recibida);

    /** Que paso con el mensaje. Todos se confirman al broker. */
    enum Resultado {
        /** Se aplico la clasificacion: el ticket quedo CLASIFICADO o ESCALADO. */
        APLICADA,
        /** La IA no pudo clasificar: el ticket sigue en PENDIENTE_CLASIFICACION. */
        QUEDA_PENDIENTE,
        /** El mensaje ya se habia procesado: no se hace nada. */
        DUPLICADA,
        /** El ticket ya no esta pendiente (por ejemplo, se reclasifico por REST antes): no se pisa. */
        IGNORADA,
        /** No existe el ticket (por ejemplo, un mensaje publicado a mano en una prueba): no hay nada que aplicar. */
        TICKET_INEXISTENTE
    }

    /**
     * La respuesta de la IA ya traducida al dominio.
     *
     * @param mensajeId     eventId del mensaje, para la idempotencia
     * @param clasificacion null si la IA respondio con error
     * @param error         motivo del error, o null si hay clasificacion
     */
    record ClasificacionRecibida(UUID mensajeId, TicketId ticketId, Clasificacion clasificacion, String error) {

        public ClasificacionRecibida {
            Objects.requireNonNull(mensajeId, "mensajeId");
            Objects.requireNonNull(ticketId, "ticketId");
            if ((clasificacion == null) == (error == null)) {
                throw new IllegalArgumentException("Tiene que venir una clasificacion o un error, no los dos");
            }
        }

        public static ClasificacionRecibida clasificada(UUID mensajeId, TicketId ticketId, Clasificacion clasificacion) {
            return new ClasificacionRecibida(mensajeId, ticketId, clasificacion, null);
        }

        public static ClasificacionRecibida conError(UUID mensajeId, TicketId ticketId, String error) {
            return new ClasificacionRecibida(mensajeId, ticketId, null, error);
        }
    }
}
