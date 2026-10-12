import { Prioridad } from '../common/enums';
import { ReportesService } from './reportes.service';
import { ReportesRepository } from './reportes.repository';

describe('ReportesService', () => {
  const repo = { resumen: jest.fn(), sla: jest.fn(), tickets: jest.fn() };
  const servicio = new ReportesService(repo as unknown as ReportesRepository);
  beforeEach(() => {
    jest.resetAllMocks();
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-10-05T14:12:00Z'));
  });
  afterEach(() => jest.useRealTimers());
  it('incluye todos los grupos con cero y los tickets sin clasificar', async () => {
    repo.resumen.mockResolvedValue({
      total: [{ cantidad: 2 }],
      abiertos: [{ cantidad: 2 }],
      escalados: [],
      slaVencidos: [],
      porPrioridad: [{ _id: null, cantidad: 2 }],
      porCategoria: [{ _id: null, cantidad: 2 }],
      porEstado: [{ _id: 'NUEVO', cantidad: 2 }],
    });
    const resumen = await servicio.resumen();
    expect(resumen.porPrioridad).toEqual({
      P1: 0,
      P2: 0,
      P3: 0,
      P4: 0,
      SIN_PRIORIDAD: 2,
    });
    expect(resumen.porCategoria).toEqual({
      INCIDENTE: 0,
      SOLICITUD: 0,
      CONSULTA: 0,
      BUG: 0,
      SIN_CATEGORIA: 2,
    });
    expect(resumen.porEstado).toMatchObject({ NUEVO: 2, CERRADO: 0 });
    expect(resumen.escalados).toBe(0);
    expect(resumen.generadoEn).toBe('2026-10-05T14:12:00.000Z');
  });
  it('responde con ceros cuando la coleccion esta vacia', async () => {
    repo.resumen.mockResolvedValue({
      total: [],
      abiertos: [],
      escalados: [],
      slaVencidos: [],
      porPrioridad: [],
      porCategoria: [],
      porEstado: [],
    });
    expect(await servicio.resumen()).toMatchObject({
      total: 0,
      abiertos: 0,
      escalados: 0,
      slaVencidos: 0,
    });
  });
  it('calcula horas vencidas, porcentaje y null si no hay resueltos', async () => {
    repo.sla.mockResolvedValue({
      vencidos: [
        {
          ticketId: 'ticket',
          titulo: 'Sin acceso',
          prioridad: Prioridad.P2,
          estado: 'EN_CURSO',
          fechaLimiteSla: new Date('2026-10-05T10:00:00Z'),
        },
      ],
      cumplimiento: [
        { _id: Prioridad.P1, resueltos: 4, enTermino: 3 },
        { _id: Prioridad.P3, resueltos: 3, enTermino: 1 },
      ],
    });
    const reporte = await servicio.sla();
    expect(reporte.vencidos[0].horasVencido).toBe(4.2);
    expect(reporte.cumplimientoPorPrioridad).toEqual({
      P1: { resueltos: 4, enTermino: 3, porcentaje: 75 },
      P2: { resueltos: 0, enTermino: 0, porcentaje: null },
      P3: { resueltos: 3, enTermino: 1, porcentaje: 33.33 },
      P4: { resueltos: 0, enTermino: 0, porcentaje: null },
    });
  });
  it('mantiene el total de paginas aunque se solicite una pagina vacia', async () => {
    repo.tickets.mockResolvedValue({ content: [], totalElements: 21 });
    expect(await servicio.tickets({ page: 5, size: 20 })).toEqual({
      content: [],
      page: 5,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    });
  });
});
