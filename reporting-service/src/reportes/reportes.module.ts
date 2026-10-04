import { Module } from '@nestjs/common';
import { ProyeccionesModule } from '../proyecciones/proyecciones.module';
import { ReportesController } from './reportes.controller';
import { ReportesRepository } from './reportes.repository';
import { ReportesService } from './reportes.service';

@Module({
  imports: [ProyeccionesModule],
  controllers: [ReportesController],
  providers: [ReportesService, ReportesRepository],
})
export class ReportesModule {}
