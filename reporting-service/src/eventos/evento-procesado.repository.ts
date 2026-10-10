import { Injectable, OnModuleInit } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { EventoProcesado } from '../schemas/evento-procesado.schema';
import { EventoDto } from './dto/evento.dto';

@Injectable()
export class EventoProcesadoRepository implements OnModuleInit {
  constructor(
    @InjectModel(EventoProcesado.name)
    private readonly eventos: Model<EventoProcesado>,
  ) {}
  async onModuleInit() {
    await this.eventos.init();
  }

  async recibir(evento: EventoDto): Promise<EventoProcesado> {
    try {
      await this.eventos
        .updateOne(
          { eventId: evento.eventId },
          {
            $setOnInsert: {
              eventId: evento.eventId,
              evento,
              estado: 'PENDIENTE',
              recibidoEn: new Date(),
              procesadoEn: null,
            },
          },
          { upsert: true },
        )
        .exec();
    } catch (error) {
      if ((error as { code?: number }).code !== 11000) throw error;
    }
    const guardado = await this.eventos
      .findOne({ eventId: evento.eventId })
      .lean()
      .exec();
    if (!guardado) throw new Error('No se pudo recuperar el evento recibido');
    return guardado;
  }

  async completar(eventId: string): Promise<boolean> {
    const resultado = await this.eventos
      .updateOne(
        { eventId, estado: 'PENDIENTE' },
        {
          $set: { estado: 'PROCESADO', procesadoEn: new Date() },
        },
      )
      .exec();
    return resultado.modifiedCount === 1;
  }
}
