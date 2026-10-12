import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';
import { InjectConnection } from '@nestjs/mongoose';
import {
  ApiOkResponse,
  ApiTags,
  ApiServiceUnavailableResponse,
} from '@nestjs/swagger';
import { Connection } from 'mongoose';
import { HealthDto } from './health.dto';
import { ErrorDto } from '../common/error.dto';

@ApiTags('Health')
@Controller('health')
export class HealthController {
  constructor(@InjectConnection() private readonly conexion: Connection) {}
  @Get()
  @ApiOkResponse({
    type: HealthDto,
    schema: {
      example: { status: 'UP', service: 'reporting-service', version: '0.1.0' },
    },
  })
  @ApiServiceUnavailableResponse({ type: ErrorDto })
  async consultar() {
    try {
      if (!this.conexion.db) throw new Error('Sin conexion');
      await this.conexion.db.admin().ping();
    } catch {
      throw new ServiceUnavailableException();
    }
    return { status: 'UP', service: 'reporting-service', version: '0.1.0' };
  }
}
