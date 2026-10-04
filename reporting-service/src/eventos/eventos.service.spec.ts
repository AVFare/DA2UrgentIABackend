import { EventosService } from './eventos.service';
import { EventoProcesadoRepository } from './evento-procesado.repository';
import { TicketViewRepository } from '../proyecciones/ticket-view.repository';
import { eventoEjemplo, eventoEscalado } from '../../test/fixtures';

describe('EventosService', () => {
  const eventos = { recibir: jest.fn(), completar: jest.fn() };
  const vistas = { aplicar: jest.fn() };
  const servicio = new EventosService(
    eventos as unknown as EventoProcesadoRepository,
    vistas as unknown as TicketViewRepository,
  );
  beforeEach(() => {
    jest.resetAllMocks();
    eventos.completar.mockResolvedValue(true);
  });
  it('responde DUPLICADO sin volver a proyectar un evento completado', async () => {
    const evento = eventoEjemplo();
    eventos.recibir.mockResolvedValue({ estado: 'PROCESADO', evento });
    await expect(servicio.procesar(evento)).resolves.toEqual({
      eventId: evento.eventId,
      resultado: 'DUPLICADO',
    });
    expect(vistas.aplicar).not.toHaveBeenCalled();
  });
  it('si falla la proyeccion no completa el evento y un reintento lo recupera', async () => {
    const evento = eventoEjemplo();
    eventos.recibir.mockResolvedValue({ estado: 'PENDIENTE', evento });
    vistas.aplicar
      .mockRejectedValueOnce(new Error('MongoDB desconectado'))
      .mockResolvedValueOnce(undefined);
    await expect(servicio.procesar(evento)).rejects.toThrow(
      'MongoDB desconectado',
    );
    expect(eventos.completar).not.toHaveBeenCalled();
    await expect(servicio.procesar(evento)).resolves.toMatchObject({
      resultado: 'PROCESADO',
    });
  });
  it('recupera una confirmacion fallida volviendo a aplicar la proyeccion idempotente', async () => {
    const evento = eventoEjemplo();
    eventos.recibir.mockResolvedValue({ estado: 'PENDIENTE', evento });
    eventos.completar
      .mockRejectedValueOnce(new Error('Confirmacion fallida'))
      .mockResolvedValueOnce(true);
    await expect(servicio.procesar(evento)).rejects.toThrow(
      'Confirmacion fallida',
    );
    await expect(servicio.procesar(evento)).resolves.toMatchObject({
      resultado: 'PROCESADO',
    });
    expect(vistas.aplicar).toHaveBeenCalledTimes(2);
  });
  it('usa el snapshot original aunque el reintento cambie el cuerpo', async () => {
    const original = eventoEjemplo();
    eventos.recibir.mockResolvedValue({
      estado: 'PENDIENTE',
      evento: original,
    });
    await servicio.procesar({
      ...original,
      payload: {
        ticket: { ...original.payload.ticket, titulo: 'Otro titulo distinto' },
      },
    });
    expect(vistas.aplicar.mock.calls[0][0].ticket.titulo).toBe(
      original.payload.ticket.titulo,
    );
  });
  it('valida los datos del escalamiento antes de guardar', async () => {
    const evento = eventoEscalado();
    delete evento.payload.motivo;
    await expect(servicio.procesar(evento)).rejects.toMatchObject({
      response: { codigo: 'VALIDACION' },
    });
    expect(eventos.recibir).not.toHaveBeenCalled();
  });
});
