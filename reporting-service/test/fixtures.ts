import { randomUUID } from 'node:crypto';
import {
  Categoria,
  EstadoTicket,
  ModuloAfectado,
  Prioridad,
  TipoEvento,
} from '../src/common/enums';
import { EventoDto } from '../src/eventos/dto/evento.dto';

export function eventoEjemplo(cambios: Partial<EventoDto> = {}): EventoDto {
  return {
    eventId: randomUUID(),
    eventType: TipoEvento.TicketCreado,
    version: 1,
    occurredAt: '2026-10-05T14:00:00Z',
    correlationId: randomUUID(),
    source: 'ticket-service',
    payload: {
      ticket: {
        ticketId: randomUUID(),
        titulo: 'No puede ingresar nadie',
        estado: EstadoTicket.NUEVO,
        prioridad: null,
        categoria: null,
        moduloAfectado: null,
        solicitanteId: randomUUID(),
        agenteAsignadoId: null,
        fechaCreacion: '2026-10-05T14:00:00Z',
        fechaLimiteSla: null,
      },
    },
    ...cambios,
  };
}
export function eventoEscalado(): EventoDto {
  const evento = eventoEjemplo({ eventType: TipoEvento.TicketEscalado });
  evento.payload.ticket = {
    ...evento.payload.ticket,
    estado: EstadoTicket.ESCALADO,
    prioridad: Prioridad.P1,
    categoria: Categoria.INCIDENTE,
    moduloAfectado: ModuloAfectado.AUTENTICACION,
    fechaLimiteSla: '2026-10-05T15:00:00Z',
  };
  evento.payload.estadoAnterior = EstadoTicket.CLASIFICADO;
  evento.payload.motivo = 'Prioridad P1';
  return evento;
}
