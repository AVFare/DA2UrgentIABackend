import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';
import { InjectConnection } from '@nestjs/mongoose';
import { ApiOkResponse, ApiTags } from '@nestjs/swagger';
import { Connection } from 'mongoose';

@ApiTags('Health')
@Controller('health')
export class HealthController {
  constructor(@InjectConnection() private readonly conexion: Connection) {}
  @Get()
  @ApiOkResponse({
    schema: {
      example: { status: 'UP', service: 'reporting-service', version: '0.1.0' },
    },
  })
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
