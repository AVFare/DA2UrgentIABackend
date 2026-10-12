package com.urgentia.ticket.infrastructure.persistence;

import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;

/** Traduce entre el agregado Ticket y la fila JPA, en los dos sentidos. */
final class TicketPersistenceMapper {

    private TicketPersistenceMapper() {
    }

    static TicketJpaEntity aEntidad(Ticket ticket) {
        TicketJpaEntity e = new TicketJpaEntity(ticket.id().valor());
        e.setTitulo(ticket.titulo());
        e.setDescripcion(ticket.descripcion());
        e.setSolicitanteId(ticket.solicitanteId());
        e.setAgenteAsignadoId(ticket.agenteAsignadoId());
        e.setEstado(ticket.estado());
        e.setPrioridad(ticket.prioridad());
        e.setFechaLimiteSla(ticket.fechaLimiteSla());
        e.setRequiereRevisionManual(ticket.requiereRevisionManual());
        e.setMotivoEscalamiento(ticket.motivoEscalamiento());
        Clasificacion c = ticket.clasificacion();
        if (c != null) {
            e.setCategoria(c.categoria());
            e.setUrgencia(c.urgencia());
            e.setImpacto(c.impacto());
            e.setModuloAfectado(c.moduloAfectado());
            e.setRequiereEscalamiento(c.requiereEscalamiento());
            e.setConfianza(c.confianza());
            e.setJustificacion(c.justificacion());
            e.setProveedor(c.proveedor());
            e.setFechaClasificacion(c.fecha());
        }
        e.setFechaCreacion(ticket.fechaCreacion());
        e.setFechaActualizacion(ticket.fechaActualizacion());
        e.setVersion(ticket.version());
        return e;
    }

    static Ticket aDominio(TicketJpaEntity e) {
        Clasificacion clasificacion = e.getCategoria() == null ? null : new Clasificacion(
                e.getCategoria(),
                e.getUrgencia(),
                e.getImpacto(),
                e.getModuloAfectado(),
                Boolean.TRUE.equals(e.getRequiereEscalamiento()),
                e.getConfianza() == null ? 0.0 : e.getConfianza(),
                e.getJustificacion(),
                e.getProveedor(),
                e.getFechaClasificacion());
        return Ticket.reconstituir(
                TicketId.de(e.getId()),
                e.getTitulo(),
                e.getDescripcion(),
                e.getSolicitanteId(),
                e.getAgenteAsignadoId(),
                e.getEstado(),
                e.getPrioridad(),
                e.getFechaLimiteSla(),
                clasificacion,
                e.isRequiereRevisionManual(),
                e.getMotivoEscalamiento(),
                e.getFechaCreacion(),
                e.getFechaActualizacion(),
                e.getVersion());
    }
}
