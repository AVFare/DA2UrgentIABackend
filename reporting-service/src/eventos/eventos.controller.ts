import { Body, Controller, HttpCode, Post } from '@nestjs/common';
import {
  ApiAcceptedResponse,
  ApiBadRequestResponse,
  ApiOperation,
  ApiInternalServerErrorResponse,
  ApiTags,
} from '@nestjs/swagger';
import { EventoDto, ResultadoEventoDto } from './dto/evento.dto';
import { EventosService } from './eventos.service';
import { ErrorDto } from '../common/error.dto';

@ApiTags('Eventos')
@Controller('api/eventos')
export class EventosController {
  constructor(private readonly eventos: EventosService) {}
  @Post()
  @HttpCode(202)
  @ApiOperation({ summary: 'Proyectar un evento de ticket (solo red interna)' })
  @ApiAcceptedResponse({ type: ResultadoEventoDto })
  @ApiBadRequestResponse({
    type: ErrorDto,
    description: 'Sobre o payload invalidos',
  })
  @ApiInternalServerErrorResponse({ type: ErrorDto })
  procesar(@Body() evento: EventoDto) {
    return this.eventos.procesar(evento);
  }
}
