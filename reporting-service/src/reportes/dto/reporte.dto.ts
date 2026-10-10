import { ApiProperty } from '@nestjs/swagger';
import {
  Categoria,
  EstadoTicket,
  ModuloAfectado,
  Prioridad,
} from '../../common/enums';

export class TicketViewDto {
  @ApiProperty({ format: 'uuid' }) ticketId!: string;
  @ApiProperty() titulo!: string;
  @ApiProperty({ enum: EstadoTicket }) estado!: EstadoTicket;
  @ApiProperty({ enum: Prioridad, nullable: true })
  prioridad!: Prioridad | null;
  @ApiProperty({ enum: Categoria, nullable: true })
  categoria!: Categoria | null;
  @ApiProperty({ enum: ModuloAfectado, nullable: true })
  moduloAfectado!: ModuloAfectado | null;
  @ApiProperty() escalado!: boolean;
  @ApiProperty({ format: 'uuid' }) solicitanteId!: string;
  @ApiProperty({ type: String, format: 'uuid', nullable: true })
  agenteAsignadoId!: string | null;
  @ApiProperty({ format: 'date-time' }) fechaCreacion!: string;
  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  fechaLimiteSla!: string | null;
  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  fechaResolucion!: string | null;
  @ApiProperty({ format: 'date-time' }) ultimoEventoEn!: string;
}
export class ResumenDto {
  @ApiProperty() total!: number;
  @ApiProperty() abiertos!: number;
  @ApiProperty() escalados!: number;
  @ApiProperty() slaVencidos!: number;
  @ApiProperty({
    type: 'object',
    additionalProperties: { type: 'integer' },
    example: { P1: 0, P2: 0, P3: 0, P4: 0, SIN_PRIORIDAD: 0 },
  })
  porPrioridad!: Record<string, number>;
  @ApiProperty({
    type: 'object',
    additionalProperties: { type: 'integer' },
    example: {
      INCIDENTE: 0,
      SOLICITUD: 0,
      CONSULTA: 0,
      BUG: 0,
      SIN_CATEGORIA: 0,
    },
  })
  porCategoria!: Record<string, number>;
  @ApiProperty({ type: 'object', additionalProperties: { type: 'integer' } })
  porEstado!: Record<string, number>;
  @ApiProperty({ format: 'date-time' }) generadoEn!: string;
}
export class TicketVencidoDto {
  @ApiProperty({ format: 'uuid' }) ticketId!: string;
  @ApiProperty() titulo!: string;
  @ApiProperty({ enum: Prioridad, nullable: true })
  prioridad!: Prioridad | null;
  @ApiProperty({ enum: EstadoTicket }) estado!: EstadoTicket;
  @ApiProperty({ format: 'date-time' }) fechaLimiteSla!: string;
  @ApiProperty({ example: 4.2 }) horasVencido!: number;
}
export class CumplimientoDto {
  @ApiProperty() resueltos!: number;
  @ApiProperty() enTermino!: number;
  @ApiProperty({ type: Number, nullable: true, minimum: 0, maximum: 100 })
  porcentaje!: number | null;
}
export class CumplimientoPorPrioridadDto {
  @ApiProperty({ type: CumplimientoDto }) P1!: CumplimientoDto;
  @ApiProperty({ type: CumplimientoDto }) P2!: CumplimientoDto;
  @ApiProperty({ type: CumplimientoDto }) P3!: CumplimientoDto;
  @ApiProperty({ type: CumplimientoDto }) P4!: CumplimientoDto;
}
export class SlaDto {
  @ApiProperty({ type: [TicketVencidoDto] }) vencidos!: TicketVencidoDto[];
  @ApiProperty({ type: CumplimientoPorPrioridadDto })
  cumplimientoPorPrioridad!: CumplimientoPorPrioridadDto;
  @ApiProperty({ format: 'date-time' }) generadoEn!: string;
}
export class PaginaTicketsDto {
  @ApiProperty({ type: [TicketViewDto] }) content!: TicketViewDto[];
  @ApiProperty() page!: number;
  @ApiProperty() size!: number;
  @ApiProperty() totalElements!: number;
  @ApiProperty() totalPages!: number;
}
