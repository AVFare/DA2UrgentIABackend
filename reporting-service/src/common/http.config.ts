import {
  BadRequestException,
  INestApplication,
  ValidationPipe,
} from '@nestjs/common';
import { ValidationError } from 'class-validator';
import { randomUUID } from 'node:crypto';
import { Request, Response, NextFunction } from 'express';
import { correlacion } from './correlacion';
import { HttpExceptionFilter } from './http-exception.filter';
import { JsonLogger } from './json.logger';

function detalles(
  errors: ValidationError[],
  prefijo = '',
): { campo: string; mensaje: string }[] {
  return errors.flatMap((error) => {
    const campo = prefijo ? `${prefijo}.${error.property}` : error.property;
    return [
      ...Object.values(error.constraints ?? {}).map((mensaje) => ({
        campo,
        mensaje,
      })),
      ...detalles(error.children ?? [], campo),
    ];
  });
}

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
  app.useGlobalPipes(
    new ValidationPipe({
      transform: true,
      whitelist: true,
      forbidNonWhitelisted: true,
      exceptionFactory: (errors) =>
        new BadRequestException({
          codigo: 'VALIDACION',
          mensaje: 'Body o parametros invalidos',
          detalles: detalles(errors),
        }),
    }),
  );
  app.useGlobalFilters(new HttpExceptionFilter());
}
