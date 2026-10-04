import { Type } from 'class-transformer';
import {
  Equals,
  IsDefined,
  IsEnum,
  IsISO8601,
  IsNotEmpty,
  IsString,
  IsUUID,
  Length,
  Matches,
  ValidateIf,
  ValidateNested,
} from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  Categoria,
  EstadoTicket,
  ModuloAfectado,
  Prioridad,
  TipoEvento,
} from '../../common/enums';

export class TicketSnapshotDto {
  @ApiProperty({ format: 'uuid' }) @IsUUID('4') ticketId!: string;
  @ApiProperty({ minLength: 5, maxLength: 120 })
  @IsString()
  @Length(5, 120)
  titulo!: string;
  @ApiProperty({ enum: EstadoTicket })
  @IsEnum(EstadoTicket)
  estado!: EstadoTicket;
  @ApiProperty({ enum: Prioridad, nullable: true })
  @ValidateIf((_, v) => v !== null)
  @IsEnum(Prioridad)
  prioridad!: Prioridad | null;
  @ApiProperty({ enum: Categoria, nullable: true })
  @ValidateIf((_, v) => v !== null)
  @IsEnum(Categoria)
  categoria!: Categoria | null;
  @ApiProperty({ enum: ModuloAfectado, nullable: true })
  @ValidateIf((_, v) => v !== null)
  @IsEnum(ModuloAfectado)
  moduloAfectado!: ModuloAfectado | null;
  @ApiProperty({ format: 'uuid' }) @IsUUID('4') solicitanteId!: string;
  @ApiProperty({ type: String, format: 'uuid', nullable: true })
  @ValidateIf((_, v) => v !== null)
  @IsUUID('4')
  agenteAsignadoId!: string | null;
  @ApiProperty({ format: 'date-time' })
  @IsISO8601({ strict: true })
  @Matches(/Z$/)
  fechaCreacion!: string;
  @ApiProperty({ type: String, format: 'date-time', nullable: true })
  @ValidateIf((_, v) => v !== null)
  @IsISO8601({ strict: true })
  @Matches(/Z$/)
  fechaLimiteSla!: string | null;
}

export class PayloadEventoDto {
  @ApiProperty({ type: TicketSnapshotDto })
  @IsDefined()
  @ValidateNested()
  @Type(() => TicketSnapshotDto)
  ticket!: TicketSnapshotDto;
  @ApiPropertyOptional({
    enum: EstadoTicket,
    description: 'Obligatorio en cambios de estado, escalados y resueltos.',
  })
  @ValidateIf((_, v) => v !== undefined)
  @IsEnum(EstadoTicket)
  estadoAnterior?: EstadoTicket;
  @ApiPropertyOptional({ description: 'Obligatorio en TicketEscalado.' })
  @ValidateIf((_, v) => v !== undefined)
  @IsString()
  @IsNotEmpty()
  @Matches(/\S/)
  motivo?: string;
}

export class EventoDto {
  @ApiProperty({ format: 'uuid' }) @IsUUID('4') eventId!: string;
  @ApiProperty({ enum: TipoEvento }) @IsEnum(TipoEvento) eventType!: TipoEvento;
  @ApiProperty({ enum: [1] }) @Equals(1) version!: number;
  @ApiProperty({ format: 'date-time' })
  @IsISO8601({ strict: true })
  @Matches(/Z$/)
  occurredAt!: string;
  @ApiProperty({ format: 'uuid' }) @IsUUID('4') correlationId!: string;
  @ApiProperty({ enum: ['ticket-service'] })
  @Equals('ticket-service')
  source!: string;
  @ApiProperty({ type: PayloadEventoDto })
  @IsDefined()
  @ValidateNested()
  @Type(() => PayloadEventoDto)
  payload!: PayloadEventoDto;
}

export class ResultadoEventoDto {
  @ApiProperty({ format: 'uuid' }) eventId!: string;
  @ApiProperty({ enum: ['PROCESADO', 'DUPLICADO'] }) resultado!:
    'PROCESADO' | 'DUPLICADO';
}
