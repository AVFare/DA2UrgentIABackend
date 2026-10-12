package com.urgentia.ticket.infrastructure.rest.mapper;

import com.urgentia.ticket.application.port.out.Pagina;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.infrastructure.rest.dto.ClasificacionResponse;
import com.urgentia.ticket.infrastructure.rest.dto.PaginaResponse;
import com.urgentia.ticket.infrastructure.rest.dto.TicketResponse;

/** Traduce el agregado al JSON del contrato (seccion 9.2). */
public final class TicketRestMapper {

    private TicketRestMapper() {
    }

    public static TicketResponse aResponse(Ticket t) {
        Clasificacion c = t.clasificacion();
        ClasificacionResponse clasificacion = c == null ? null : new ClasificacionResponse(
                c.categoria(), c.urgencia(), c.impacto(), c.moduloAfectado(), c.requiereEscalamiento(),
                c.confianza(), c.justificacion(), c.proveedor(), c.fecha());
        return new TicketResponse(
                t.id().valor(),
                t.titulo(),
                t.descripcion(),
                t.solicitanteId(),
                t.agenteAsignadoId(),
                t.estado(),
                t.prioridad(),
                t.fechaLimiteSla(),
                t.requiereRevisionManual(),
                t.motivoEscalamiento(),
                clasificacion,
                t.fechaCreacion(),
                t.fechaActualizacion());
    }

    public static PaginaResponse<TicketResponse> aResponse(Pagina<Ticket> pagina) {
        return new PaginaResponse<>(pagina.contenido().stream().map(TicketRestMapper::aResponse).toList(),
                pagina.pagina(), pagina.tamanio(), pagina.totalElementos(), pagina.totalPaginas());
    }
}
