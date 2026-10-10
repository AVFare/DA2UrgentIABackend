import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import {
  Categoria,
  EstadoTicket,
  ModuloAfectado,
  Prioridad,
} from '../common/enums';

@Schema({ collection: 'ticket_view', versionKey: false })
export class TicketView {
  @Prop({ required: true, unique: true }) ticketId!: string;
  @Prop({ required: true }) titulo!: string;
  @Prop({ required: true, enum: EstadoTicket }) estado!: EstadoTicket;
  @Prop({ type: String, enum: Object.values(Prioridad), default: null })
  prioridad!: Prioridad | null;
  @Prop({ type: String, enum: Object.values(Categoria), default: null })
  categoria!: Categoria | null;
  @Prop({ type: String, enum: Object.values(ModuloAfectado), default: null })
  moduloAfectado!: ModuloAfectado | null;
  @Prop({ required: true }) escalado!: boolean;
  @Prop({ required: true }) solicitanteId!: string;
  @Prop({ type: String, default: null }) agenteAsignadoId!: string | null;
  @Prop({ required: true, type: Date }) fechaCreacion!: Date;
  @Prop({ type: Date, default: null }) fechaLimiteSla!: Date | null;
  @Prop({ type: Date, default: null }) fechaResolucion!: Date | null;
  @Prop({ required: true, type: Date }) ultimoEventoEn!: Date;
}
export const TicketViewSchema = SchemaFactory.createForClass(TicketView);
TicketViewSchema.index({
  prioridad: 1,
  estado: 1,
  fechaCreacion: -1,
  ticketId: 1,
});
TicketViewSchema.index({ estado: 1, fechaLimiteSla: 1 });
