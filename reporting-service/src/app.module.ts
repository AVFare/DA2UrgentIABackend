import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { HealthController } from './health/health.controller';

@Module({
  imports: [
    MongooseModule.forRootAsync({
      useFactory: () => {
        const uri = process.env.MONGO_URI;
        if (!uri) throw new Error('Falta la variable MONGO_URI');
        return { uri, serverSelectionTimeoutMS: 5000, retryAttempts: 0 };
      },
    }),
  ],
  controllers: [HealthController],
})
export class AppModule {}
