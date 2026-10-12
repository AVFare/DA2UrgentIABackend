import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { configurarHttp } from './common/http.config';
import { JsonLogger } from './common/json.logger';
import { configurarSwagger } from './common/swagger.config';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { logger: new JsonLogger() });
  configurarHttp(app);
  configurarSwagger(app);
  app.enableShutdownHooks();
  await app.listen(8085, '0.0.0.0');
}
void bootstrap();
