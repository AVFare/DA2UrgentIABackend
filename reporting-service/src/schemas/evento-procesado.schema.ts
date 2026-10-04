import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Schema as MongoSchema } from 'mongoose';
import { EventoDto } from '../eventos/dto/evento.dto';

@Schema({ collection: 'eventos_procesados', versionKey: false })
export class EventoProcesado {
  @Prop({ required: true, unique: true }) eventId!: string;
  @Prop({ required: true, type: MongoSchema.Types.Mixed }) evento!: EventoDto;
  @Prop({ required: true, enum: ['PENDIENTE', 'PROCESADO'] }) estado!:
    'PENDIENTE' | 'PROCESADO';
  @Prop({ required: true, type: Date }) recibidoEn!: Date;
  @Prop({ type: Date, default: null }) procesadoEn!: Date | null;
}
export const EventoProcesadoSchema =
  SchemaFactory.createForClass(EventoProcesado);
