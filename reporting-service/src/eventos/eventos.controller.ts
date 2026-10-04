import { Body, Controller, HttpCode, Post } from '@nestjs/common';
import {
  ApiAcceptedResponse,
  ApiBadRequestResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import { EventoDto, ResultadoEventoDto } from './dto/evento.dto';
import { EventosService } from './eventos.service';

@ApiTags('Eventos')
@Controller('api/eventos')
export class EventosController {
  constructor(private readonly eventos: EventosService) {}
  @Post()
  @HttpCode(202)
  @ApiOperation({ summary: 'Proyectar un evento de ticket (solo red interna)' })
  @ApiAcceptedResponse({ type: ResultadoEventoDto })
  @ApiBadRequestResponse({ description: 'Sobre o payload invalidos' })
  procesar(@Body() evento: EventoDto) {
    return this.eventos.procesar(evento);
  }
}
