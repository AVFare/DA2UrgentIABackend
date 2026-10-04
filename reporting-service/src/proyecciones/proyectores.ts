import { EstadoTicket, TipoEvento } from '../common/enums';
import { EventoDto } from '../eventos/dto/evento.dto';
import { Proyeccion, Proyector } from './proyector.interface';

abstract class SnapshotProyector implements Proyector {
  abstract readonly tipo: TipoEvento;
  proyectar(evento: EventoDto): Proyeccion {
    const t = evento.payload.ticket;
    return {
      ticket: {
        ticketId: t.ticketId,
        titulo: t.titulo,
        estado: t.estado,
        prioridad: t.prioridad,
        categoria: t.categoria,
        moduloAfectado: t.moduloAfectado,
        escalado: t.estado === EstadoTicket.ESCALADO,
        solicitanteId: t.solicitanteId,
        agenteAsignadoId: t.agenteAsignadoId,
        fechaCreacion: new Date(t.fechaCreacion),
        fechaLimiteSla:
          t.fechaLimiteSla === null ? null : new Date(t.fechaLimiteSla),
        ultimoEventoEn: new Date(evento.occurredAt),
      },
      // TicketEstadoCambiado puede preceder a TicketResuelto con el mismo snapshot.
      resolucion:
        t.estado === EstadoTicket.RESUELTO
          ? 'RESOLVER'
          : t.estado === EstadoTicket.CERRADO
            ? 'CONSERVAR'
            : 'LIMPIAR',
    };
  }
}

export class TicketCreadoProyector extends SnapshotProyector {
  readonly tipo = TipoEvento.TicketCreado;
}
export class TicketClasificadoProyector extends SnapshotProyector {
  readonly tipo = TipoEvento.TicketClasificado;
}
export class TicketEscaladoProyector extends SnapshotProyector {
  readonly tipo = TipoEvento.TicketEscalado;
}
export class TicketAsignadoProyector extends SnapshotProyector {
  readonly tipo = TipoEvento.TicketAsignado;
}
export class TicketEstadoCambiadoProyector extends SnapshotProyector {
  readonly tipo = TipoEvento.TicketEstadoCambiado;
}
export class TicketResueltoProyector extends SnapshotProyector {
  readonly tipo = TipoEvento.TicketResuelto;
}

export const PROYECTORES: Proyector[] = [
  new TicketCreadoProyector(),
  new TicketClasificadoProyector(),
  new TicketEscaladoProyector(),
  new TicketAsignadoProyector(),
  new TicketEstadoCambiadoProyector(),
  new TicketResueltoProyector(),
];
