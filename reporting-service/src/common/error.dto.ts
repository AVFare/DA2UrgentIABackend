import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

class DetalleErrorDto {
  @ApiProperty() campo!: string;
  @ApiProperty() mensaje!: string;
}
export class ErrorDto {
  @ApiProperty({ example: 'VALIDACION' }) codigo!: string;
  @ApiProperty() mensaje!: string;
  @ApiPropertyOptional({ type: [DetalleErrorDto] })
  detalles?: DetalleErrorDto[];
  @ApiProperty({ format: 'date-time' }) timestamp!: string;
  @ApiProperty() path!: string;
  @ApiProperty() correlationId!: string;
}
