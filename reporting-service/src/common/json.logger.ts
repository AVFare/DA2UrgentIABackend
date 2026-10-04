import { LoggerService } from '@nestjs/common';
import { correlacion } from './correlacion';

export class JsonLogger implements LoggerService {
  private escribir(level: string, message: unknown) {
    const niveles: Record<string, number> = {
      DEBUG: 0,
      INFO: 1,
      WARN: 2,
      ERROR: 3,
    };
    const minimo = niveles[process.env.LOG_LEVEL?.toUpperCase() ?? 'INFO'] ?? 1;
    if (niveles[level] < minimo) return;
    process.stdout.write(
      JSON.stringify({
        timestamp: new Date().toISOString(),
        level,
        service: 'reporting-service',
        correlationId: correlacion.getStore() ?? null,
        message: typeof message === 'string' ? message : 'Evento de aplicacion',
      }) + '\n',
    );
  }
  log(message: unknown) {
    this.escribir('INFO', message);
  }
  error(message: unknown) {
    this.escribir('ERROR', message);
  }
  warn(message: unknown) {
    this.escribir('WARN', message);
  }
  debug(message: unknown) {
    this.escribir('DEBUG', message);
  }
  verbose(message: unknown) {
    this.escribir('DEBUG', message);
  }
  fatal(message: unknown) {
    this.escribir('ERROR', message);
  }
}
