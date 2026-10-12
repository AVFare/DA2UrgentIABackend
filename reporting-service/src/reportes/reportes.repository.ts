import { Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { ESTADOS_TERMINADOS, Prioridad } from '../common/enums';
import { TicketView } from '../schemas/ticket-view.schema';
import { ConsultaTicketsDto } from './dto/consulta-tickets.dto';

export interface Conteo {
  _id: string | null;
  cantidad: number;
}
export interface ResumenAgregado {
  total: { cantidad: number }[];
  abiertos: { cantidad: number }[];
  escalados: { cantidad: number }[];
  slaVencidos: { cantidad: number }[];
  porPrioridad: Conteo[];
  porCategoria: Conteo[];
  porEstado: Conteo[];
}
export interface CumplimientoAgregado {
  _id: Prioridad;
  resueltos: number;
  enTermino: number;
}
export interface SlaAgregado {
  vencidos: Pick<
    TicketView,
    'ticketId' | 'titulo' | 'prioridad' | 'estado' | 'fechaLimiteSla'
  >[];
  cumplimiento: CumplimientoAgregado[];
}
@Injectable()
export class ReportesRepository {
  constructor(
    @InjectModel(TicketView.name) private readonly vistas: Model<TicketView>,
  ) {}
  private vencidos(ahora: Date) {
    return {
      estado: { $nin: ESTADOS_TERMINADOS },
      fechaLimiteSla: { $ne: null, $lt: ahora },
    };
  }
  async resumen(ahora: Date): Promise<ResumenAgregado> {
    const agrupar = (campo: string) => [
      { $group: { _id: `$${campo}`, cantidad: { $sum: 1 } } },
    ];
    const [resultado] = await this.vistas
      .aggregate<ResumenAgregado>([
        {
          $facet: {
            total: [{ $count: 'cantidad' }],
            abiertos: [
              { $match: { estado: { $nin: ESTADOS_TERMINADOS } } },
              { $count: 'cantidad' },
            ],
            escalados: [{ $match: { escalado: true } }, { $count: 'cantidad' }],
            slaVencidos: [
              { $match: this.vencidos(ahora) },
              { $count: 'cantidad' },
            ],
            porPrioridad: agrupar('prioridad'),
            porCategoria: agrupar('categoria'),
            porEstado: agrupar('estado'),
          },
        },
      ])
      .exec();
    return resultado;
  }
  async sla(ahora: Date): Promise<SlaAgregado> {
    const [resultado] = await this.vistas
      .aggregate<SlaAgregado>([
        {
          $facet: {
            vencidos: [
              { $match: this.vencidos(ahora) },
              { $sort: { fechaLimiteSla: 1, ticketId: 1 } },
              {
                $project: {
                  _id: 0,
                  ticketId: 1,
                  titulo: 1,
                  prioridad: 1,
                  estado: 1,
                  fechaLimiteSla: 1,
                },
              },
            ],
            cumplimiento: [
              {
                $match: {
                  estado: { $in: ESTADOS_TERMINADOS },
                  prioridad: { $in: Object.values(Prioridad) },
                  fechaResolucion: { $ne: null },
                  fechaLimiteSla: { $ne: null },
                },
              },
              {
                $group: {
                  _id: '$prioridad',
                  resueltos: { $sum: 1 },
                  enTermino: {
                    $sum: {
                      $cond: [
                        { $lte: ['$fechaResolucion', '$fechaLimiteSla'] },
                        1,
                        0,
                      ],
                    },
                  },
                },
              },
            ],
          },
        },
      ])
      .exec();
    return resultado;
  }
  async tickets(
    consulta: ConsultaTicketsDto,
  ): Promise<{ content: TicketView[]; totalElements: number }> {
    const filtro = {
      ...(consulta.prioridad ? { prioridad: consulta.prioridad } : {}),
      ...(consulta.estado ? { estado: consulta.estado } : {}),
    };
    const [pagina] = await this.vistas
      .aggregate<{ content: TicketView[]; total: { cantidad: number }[] }>([
        { $match: filtro },
        { $sort: { fechaCreacion: -1, ticketId: 1 } },
        {
          $facet: {
            content: [
              { $skip: consulta.page * consulta.size },
              { $limit: consulta.size },
              { $project: { _id: 0 } },
            ],
            total: [{ $count: 'cantidad' }],
          },
        },
      ])
      .exec();
    return {
      content: pagina.content,
      totalElements: pagina.total[0]?.cantidad ?? 0,
    };
  }
}
