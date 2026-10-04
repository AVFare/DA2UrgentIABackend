import { Controller, Get, Query } from '@nestjs/common';
import {
  ApiHeader,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
  ApiBadRequestResponse,
  ApiInternalServerErrorResponse,
} from '@nestjs/swagger';
import { ConsultaTicketsDto } from './dto/consulta-tickets.dto';
import { PaginaTicketsDto, ResumenDto, SlaDto } from './dto/reporte.dto';
import { ReportesService } from './reportes.service';
import { ErrorDto } from '../common/error.dto';

@ApiTags('Reportes')
@ApiBadRequestResponse({ type: ErrorDto })
@ApiInternalServerErrorResponse({ type: ErrorDto })
@ApiHeader({ name: 'X-Correlation-Id', required: false })
@Controller('api/reportes')
export class ReportesController {
  constructor(private readonly reportes: ReportesService) {}
  @Get('resumen')
  @ApiOperation({ summary: 'Totales y distribuciones de tickets' })
  @ApiOkResponse({ type: ResumenDto })
  resumen() {
    return this.reportes.resumen();
  }
  @Get('sla')
  @ApiOperation({ summary: 'Tickets vencidos y cumplimiento por prioridad' })
  @ApiOkResponse({ type: SlaDto })
  sla() {
    return this.reportes.sla();
  }
  @Get('tickets')
  @ApiOperation({ summary: 'Vista de tickets filtrada y paginada' })
  @ApiOkResponse({ type: PaginaTicketsDto })
  tickets(@Query() consulta: ConsultaTicketsDto) {
    return this.reportes.tickets(consulta);
  }
}
