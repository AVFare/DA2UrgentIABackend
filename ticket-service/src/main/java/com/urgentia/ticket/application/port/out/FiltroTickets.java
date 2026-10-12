package com.urgentia.ticket.application.port.out;

import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Prioridad;
import java.util.UUID;

/** Filtros opcionales de la busqueda de tickets. Un campo null no filtra. */
public record FiltroTickets(EstadoTicket estado, Prioridad prioridad, Categoria categoria, UUID solicitanteId) {

    public static FiltroTickets sinFiltros() {
        return new FiltroTickets(null, null, null, null);
    }

    public FiltroTickets conSolicitante(UUID solicitante) {
        return new FiltroTickets(estado, prioridad, categoria, solicitante);
    }
}
