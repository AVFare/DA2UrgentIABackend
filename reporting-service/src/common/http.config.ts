import { INestApplication } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { Request, Response, NextFunction } from 'express';
import { correlacion } from './correlacion';
import { HttpExceptionFilter } from './http-exception.filter';
import { JsonLogger } from './json.logger';
import { crearValidationPipe } from './validacion';

export function configurarHttp(app: INestApplication) {
  const logger = new JsonLogger();
  app.use((req: Request, res: Response, next: NextFunction) => {
    const recibido = req.get('X-Correlation-Id');
    const id = recibido?.trim() || randomUUID();
    res.setHeader('X-Correlation-Id', id);
    correlacion.run(id, () => {
      res.on('finish', () =>
        correlacion.run(id, () =>
          logger.log(`${req.method} ${req.path} ${res.statusCode}`),
        ),
      );
      next();
    });
  });
  app.useGlobalPipes(crearValidationPipe());
  app.useGlobalFilters(new HttpExceptionFilter());
}
