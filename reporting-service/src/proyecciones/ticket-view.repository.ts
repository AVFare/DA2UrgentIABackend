import { Injectable, OnModuleInit } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, PipelineStage } from 'mongoose';
import { TicketView } from '../schemas/ticket-view.schema';
import { Proyeccion } from './proyector.interface';

@Injectable()
export class TicketViewRepository implements OnModuleInit {
  constructor(
    @InjectModel(TicketView.name) private readonly vistas: Model<TicketView>,
  ) {}
  async onModuleInit() {
    await this.vistas.init();
  }

  async aplicar({ ticket, resolucion }: Proyeccion): Promise<void> {
    // Comparacion y actualizacion en una sola operacion atomica: evita carreras
    // entre eventos de un mismo ticket y funciona sin replica set/transacciones.
    const aplica = {
      $lte: [
        { $ifNull: ['$ultimoEventoEn', ticket.ultimoEventoEn] },
        ticket.ultimoEventoEn,
      ],
    };
    const cambios: Record<string, unknown> = {};
    for (const [campo, valor] of Object.entries(ticket)) {
      cambios[campo] = { $cond: [aplica, { $literal: valor }, `$${campo}`] };
    }
    const fechaResolucion =
      resolucion === 'RESOLVER'
        ? { $ifNull: ['$fechaResolucion', ticket.ultimoEventoEn] }
        : resolucion === 'CONSERVAR'
          ? { $ifNull: ['$fechaResolucion', null] }
          : null;
    cambios.fechaResolucion = {
      $cond: [aplica, fechaResolucion, '$fechaResolucion'],
    };
    const actualizar = () =>
      this.vistas
        .updateOne(
          { ticketId: ticket.ticketId },
          [{ $set: cambios }] as PipelineStage[],
          { upsert: true },
        )
        .exec();
    try {
      await actualizar();
    } catch (error) {
      // Dos primeros eventos pueden intentar insertar a la vez: el indice
      // unico protege ticketId; reintentar vuelve a evaluar la fecha en Mongo.
      if ((error as { code?: number }).code !== 11000) throw error;
      await actualizar();
    }
  }
}
