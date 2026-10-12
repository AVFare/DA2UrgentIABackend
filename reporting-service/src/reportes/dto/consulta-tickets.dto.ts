import { Transform } from 'class-transformer';
import { IsEnum, IsInt, IsOptional, Max, Min } from 'class-validator';
import { ApiPropertyOptional } from '@nestjs/swagger';
import { EstadoTicket, Prioridad } from '../../common/enums';

const numeroEntero = ({ value }: { value: unknown }) =>
  typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value;
const vacio = ({ value }: { value: unknown }) =>
  value === '' ? undefined : value;

export class ConsultaTicketsDto {
  @ApiPropertyOptional({ enum: Prioridad })
  @Transform(vacio)
  @IsOptional()
  @IsEnum(Prioridad)
  prioridad?: Prioridad;
  @ApiPropertyOptional({ enum: EstadoTicket })
  @Transform(vacio)
  @IsOptional()
  @IsEnum(EstadoTicket)
  estado?: EstadoTicket;
  @ApiPropertyOptional({ default: 0, minimum: 0 })
  @Transform(numeroEntero)
  @IsInt()
  @Min(0)
  @Max(Math.floor(Number.MAX_SAFE_INTEGER / 100))
  page = 0;
  @ApiPropertyOptional({ default: 20, minimum: 1, maximum: 100 })
  @Transform(numeroEntero)
  @IsInt()
  @Min(1)
  @Max(100)
  size = 20;
}
