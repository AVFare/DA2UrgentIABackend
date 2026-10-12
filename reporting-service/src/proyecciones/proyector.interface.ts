import { EventoDto } from '../eventos/dto/evento.dto';
import { TipoEvento } from '../common/enums';
import { TicketView } from '../schemas/ticket-view.schema';

export interface Proyeccion {
  ticket: Omit<TicketView, 'fechaResolucion'>;
  resolucion: 'RESOLVER' | 'CONSERVAR' | 'LIMPIAR';
}
export interface Proyector {
  readonly tipo: TipoEvento;
  proyectar(evento: EventoDto): Proyeccion;
}
