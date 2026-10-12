import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { TicketView, TicketViewSchema } from '../schemas/ticket-view.schema';
import { TicketViewRepository } from './ticket-view.repository';

@Module({
  imports: [
    MongooseModule.forFeature([
      { name: TicketView.name, schema: TicketViewSchema },
    ]),
  ],
  providers: [TicketViewRepository],
  exports: [TicketViewRepository, MongooseModule],
})
export class ProyeccionesModule {}
