import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { ProyeccionesModule } from '../proyecciones/proyecciones.module';
import {
  EventoProcesado,
  EventoProcesadoSchema,
} from '../schemas/evento-procesado.schema';
import { EventoProcesadoRepository } from './evento-procesado.repository';
import { EventosController } from './eventos.controller';
import { EventosService } from './eventos.service';
import { ConsumidorEventosService } from './consumidor-eventos.service';
import { proveedoresRabbitmq } from './rabbitmq.config';

@Module({
  imports: [
    ProyeccionesModule,
    MongooseModule.forFeature([
      { name: EventoProcesado.name, schema: EventoProcesadoSchema },
    ]),
  ],
  controllers: [EventosController],
  providers: [
    EventosService,
    EventoProcesadoRepository,
    ConsumidorEventosService,
    ...proveedoresRabbitmq,
  ],
})
export class EventosModule {}
