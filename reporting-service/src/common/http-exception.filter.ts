import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
} from '@nestjs/common';
import { Request, Response } from 'express';
import { correlacion } from './correlacion';
import { JsonLogger } from './json.logger';

@Catch()
export class HttpExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    const req = host.switchToHttp().getRequest<Request>();
    const res = host.switchToHttp().getResponse<Response>();
    const status =
      exception instanceof HttpException ? exception.getStatus() : 500;
    const contenido =
      exception instanceof HttpException ? exception.getResponse() : {};
    const body =
      typeof contenido === 'object'
        ? (contenido as Record<string, unknown>)
        : {};
    const codigos: Record<number, string> = {
      400: 'VALIDACION',
      401: 'NO_AUTENTICADO',
      403: 'SIN_PERMISO',
      404: 'NO_ENCONTRADO',
    };
    if (status >= 500) new JsonLogger().error(`Fallo HTTP ${status}`);
    res.status(status).json({
      codigo: body.codigo ?? codigos[status] ?? 'ERROR_INTERNO',
      mensaje:
        status >= 500
          ? 'No se pudo completar la solicitud'
          : (body.mensaje ??
            (status === 404 ? 'Recurso no encontrado' : 'Solicitud invalida')),
      ...(body.detalles ? { detalles: body.detalles } : {}),
      timestamp: new Date().toISOString(),
      path: req.path,
      correlationId:
        correlacion.getStore() ?? res.getHeader('X-Correlation-Id') ?? null,
    });
  }
}
