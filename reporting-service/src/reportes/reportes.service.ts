import { Injectable } from '@nestjs/common';
import { Categoria, EstadoTicket, Prioridad } from '../common/enums';
import { ConsultaTicketsDto } from './dto/consulta-tickets.dto';
import {
  CumplimientoPorPrioridadDto,
  PaginaTicketsDto,
  ResumenDto,
  SlaDto,
  TicketViewDto,
} from './dto/reporte.dto';
import { Conteo, ReportesRepository } from './reportes.repository';
import { TicketView } from '../schemas/ticket-view.schema';

function distribucion(
  claves: string[],
  grupos: Conteo[],
  sinValor?: string,
): Record<string, number> {
  const resultado = Object.fromEntries(claves.map((c) => [c, 0]));
  if (sinValor) resultado[sinValor] = 0;
  for (const grupo of grupos) {
    const clave = grupo._id ?? sinValor;
    if (clave && clave in resultado) resultado[clave] = grupo.cantidad;
  }
  return resultado;
}
export function ticketDto(t: TicketView): TicketViewDto {
  return {
    ticketId: t.ticketId,
    titulo: t.titulo,
    estado: t.estado,
    prioridad: t.prioridad,
    categoria: t.categoria,
    moduloAfectado: t.moduloAfectado,
    escalado: t.escalado,
    solicitanteId: t.solicitanteId,
    agenteAsignadoId: t.agenteAsignadoId,
    fechaCreacion: t.fechaCreacion.toISOString(),
    fechaLimiteSla: t.fechaLimiteSla?.toISOString() ?? null,
    fechaResolucion: t.fechaResolucion?.toISOString() ?? null,
    ultimoEventoEn: t.ultimoEventoEn.toISOString(),
  };
}
@Injectable()
export class ReportesService {
  constructor(private readonly reportes: ReportesRepository) {}
  async resumen(): Promise<ResumenDto> {
    const ahora = new Date();
    const r = await this.reportes.resumen(ahora);
    return {
      total: r.total[0]?.cantidad ?? 0,
      abiertos: r.abiertos[0]?.cantidad ?? 0,
      escalados: r.escalados[0]?.cantidad ?? 0,
      slaVencidos: r.slaVencidos[0]?.cantidad ?? 0,
      porPrioridad: distribucion(
        Object.values(Prioridad),
        r.porPrioridad,
        'SIN_PRIORIDAD',
      ),
      porCategoria: distribucion(
        Object.values(Categoria),
        r.porCategoria,
        'SIN_CATEGORIA',
      ),
      porEstado: distribucion(Object.values(EstadoTicket), r.porEstado),
      generadoEn: ahora.toISOString(),
    };
  }
  async sla(): Promise<SlaDto> {
    const ahora = new Date();
    const r = await this.reportes.sla(ahora);
    const cumplimientoPorPrioridad = Object.fromEntries(
      Object.values(Prioridad).map((p) => {
        const conteo = r.cumplimiento.find((c) => c._id === p);
        const resueltos = conteo?.resueltos ?? 0,
          enTermino = conteo?.enTermino ?? 0;
        return [
          p,
          {
            resueltos,
            enTermino,
            porcentaje: resueltos
              ? Math.round((enTermino / resueltos) * 10000) / 100
              : null,
          },
        ];
      }),
    ) as unknown as CumplimientoPorPrioridadDto;
    return {
      vencidos: r.vencidos.map((t) => ({
        ticketId: t.ticketId,
        titulo: t.titulo,
        prioridad: t.prioridad,
        estado: t.estado,
        fechaLimiteSla: t.fechaLimiteSla!.toISOString(),
        horasVencido:
          Math.round(
            ((ahora.getTime() - t.fechaLimiteSla!.getTime()) / 3600000) * 10,
          ) / 10,
      })),
      cumplimientoPorPrioridad,
      generadoEn: ahora.toISOString(),
    };
  }
  async tickets(consulta: ConsultaTicketsDto): Promise<PaginaTicketsDto> {
    const r = await this.reportes.tickets(consulta);
    return {
      content: r.content.map(ticketDto),
      page: consulta.page,
      size: consulta.size,
      totalElements: r.totalElements,
      totalPages: Math.ceil(r.totalElements / consulta.size),
    };
  }
}
