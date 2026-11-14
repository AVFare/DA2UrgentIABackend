import { BadRequestException, ValidationPipe } from '@nestjs/common';
import { ValidationError } from 'class-validator';

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

/** HTTP y AMQP aceptan exactamente el mismo contrato de eventos. */
export function crearValidationPipe(): ValidationPipe {
  return new ValidationPipe({
    transform: true,
    whitelist: true,
    forbidNonWhitelisted: true,
    exceptionFactory: (errors) =>
      new BadRequestException({
        codigo: 'VALIDACION',
        mensaje: 'Body o parametros invalidos',
        detalles: detalles(errors),
      }),
  });
}
