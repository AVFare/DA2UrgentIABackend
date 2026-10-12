package com.urgentia.ticket.application.port.out;

import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import java.util.Optional;

/**
 * Repository (puerto de salida): la aplicacion guarda y busca tickets sin saber
 * que hay una base de datos detras. Lo implementa un adaptador JPA en infrastructure.
 */
public interface TicketRepository {

    /** Guarda el ticket y devuelve la version guardada (con su version de concurrencia actualizada). */
    Ticket guardar(Ticket ticket);

    Optional<Ticket> buscarPorId(TicketId id);

    /** Orden: prioridad ascendente (sin prioridad al final) y fecha de creacion descendente. */
    Pagina<Ticket> buscar(FiltroTickets filtro, int pagina, int tamanio);
}
