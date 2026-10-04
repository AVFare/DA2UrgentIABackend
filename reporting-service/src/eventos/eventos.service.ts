import { BadRequestException, Injectable } from '@nestjs/common';
import { EstadoTicket, TipoEvento } from '../common/enums';
import { correlacion } from '../common/correlacion';
import { JsonLogger } from '../common/json.logger';
import { PROYECTORES } from '../proyecciones/proyectores';
import { TicketViewRepository } from '../proyecciones/ticket-view.repository';
import { EventoDto, ResultadoEventoDto } from './dto/evento.dto';
import { EventoProcesadoRepository } from './evento-procesado.repository';

@Injectable()
export class EventosService {
  constructor(
    private readonly eventos: EventoProcesadoRepository,
    private readonly vistas: TicketViewRepository,
  ) {}

  async procesar(evento: EventoDto): Promise<ResultadoEventoDto> {
    this.validarPayload(evento);
    return correlacion.run(evento.correlationId, async () => {
      const recibido = await this.eventos.recibir(evento);
      if (recibido.estado === 'PROCESADO')
        return { eventId: evento.eventId, resultado: 'DUPLICADO' };
      // En un reintento se usa el cuerpo guardado originalmente; un eventId
      // repetido nunca permite reemplazar el snapshot por otro cuerpo.
      const proyector = PROYECTORES.find(
        (p) => p.tipo === recibido.evento.eventType,
      );
      if (!proyector)
        throw new Error('No hay proyector para el evento guardado');
      await this.vistas.aplicar(proyector.proyectar(recibido.evento));
      const completado = await this.eventos.completar(evento.eventId);
      new JsonLogger().log(
        `Evento ${evento.eventId} ${evento.eventType}: ${completado ? 'PROCESADO' : 'DUPLICADO'}`,
      );
      return {
        eventId: evento.eventId,
        resultado: completado ? 'PROCESADO' : 'DUPLICADO',
      };
    });
  }

  private validarPayload(evento: EventoDto) {
    const detalles: { campo: string; mensaje: string }[] = [];
    const conEstadoAnterior = [
      TipoEvento.TicketEstadoCambiado,
      TipoEvento.TicketEscalado,
      TipoEvento.TicketResuelto,
    ];
    if (
      conEstadoAnterior.includes(evento.eventType) &&
      evento.payload.estadoAnterior === undefined
    ) {
      detalles.push({
        campo: 'payload.estadoAnterior',
        mensaje: 'Es obligatorio para este tipo de evento',
      });
    }
    if (
      evento.eventType === TipoEvento.TicketEscalado &&
      !evento.payload.motivo?.trim()
    ) {
      detalles.push({
        campo: 'payload.motivo',
        mensaje: 'Es obligatorio para TicketEscalado',
      });
    }
    if (
      evento.eventType === TipoEvento.TicketResuelto &&
      evento.payload.ticket.estado !== EstadoTicket.RESUELTO
    ) {
      detalles.push({
        campo: 'payload.ticket.estado',
        mensaje: 'TicketResuelto debe tener estado RESUELTO',
      });
    }
    if (
      evento.eventType === TipoEvento.TicketEscalado &&
      evento.payload.ticket.estado !== EstadoTicket.ESCALADO
    ) {
      detalles.push({
        campo: 'payload.ticket.estado',
        mensaje: 'TicketEscalado debe tener estado ESCALADO',
      });
    }
    if (detalles.length)
      throw new BadRequestException({
        codigo: 'VALIDACION',
        mensaje: 'Payload incompatible con el tipo de evento',
        detalles,
      });
  }
}
