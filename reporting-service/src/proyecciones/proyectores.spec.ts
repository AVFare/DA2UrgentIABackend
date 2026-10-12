import { randomUUID } from 'node:crypto';
import {
  Categoria,
  EstadoTicket,
  ModuloAfectado,
  Prioridad,
  TipoEvento,
} from '../common/enums';
import { EventoDto } from '../eventos/dto/evento.dto';
import { PROYECTORES } from './proyectores';

const evento: EventoDto = {
  eventId: randomUUID(),
  eventType: TipoEvento.TicketEscalado,
  version: 1,
  occurredAt: '2026-10-05T14:03:12Z',
  correlationId: randomUUID(),
  source: 'ticket-service',
  payload: {
    ticket: {
      ticketId: randomUUID(),
      titulo: 'Produccion caida',
      estado: EstadoTicket.ESCALADO,
      prioridad: Prioridad.P1,
      categoria: Categoria.INCIDENTE,
      moduloAfectado: ModuloAfectado.AUTENTICACION,
      solicitanteId: randomUUID(),
      agenteAsignadoId: null,
      fechaCreacion: '2026-10-05T14:03:10Z',
      fechaLimiteSla: '2026-10-05T15:03:11Z',
    },
    estadoAnterior: EstadoTicket.CLASIFICADO,
    motivo: 'Prioridad P1',
  },
};
describe('Proyectores', () => {
  it('cubre exactamente los seis tipos de evento', () => {
    expect(PROYECTORES.map((p) => p.tipo).sort()).toEqual(
      Object.values(TipoEvento).sort(),
    );
  });
  it.each(PROYECTORES.map((p) => [p.tipo, p] as const))(
    '%s usa el snapshot sin recalcular prioridad ni SLA',
    (_, p) => {
      const vista = p.proyectar({ ...evento, eventType: p.tipo }).ticket;
      expect(vista).toMatchObject({
        estado: EstadoTicket.ESCALADO,
        prioridad: Prioridad.P1,
        escalado: true,
        fechaLimiteSla: new Date('2026-10-05T15:03:11Z'),
        ultimoEventoEn: new Date(evento.occurredAt),
      });
    },
  );
  it('conserva prioridad y SLA nulos en tickets sin clasificar', () => {
    const p = PROYECTORES[0].proyectar({
      ...evento,
      payload: {
        ticket: {
          ...evento.payload.ticket,
          estado: EstadoTicket.PENDIENTE_CLASIFICACION,
          prioridad: null,
          categoria: null,
          moduloAfectado: null,
          fechaLimiteSla: null,
        },
      },
    });
    expect(p.ticket).toMatchObject({
      prioridad: null,
      fechaLimiteSla: null,
      escalado: false,
    });
  });
  it.each([
    [EstadoTicket.RESUELTO, 'RESOLVER'],
    [EstadoTicket.CERRADO, 'CONSERVAR'],
    [EstadoTicket.EN_CURSO, 'LIMPIAR'],
  ])(
    '%s aplica la politica correcta de fecha de resolucion',
    (estado, resolucion) => {
      expect(
        PROYECTORES[4].proyectar({
          ...evento,
          payload: {
            ticket: {
              ...evento.payload.ticket,
              estado: estado as EstadoTicket,
            },
          },
        }).resolucion,
      ).toBe(resolucion);
    },
  );
});
