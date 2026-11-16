import {
  BadRequestException,
  BeforeApplicationShutdown,
  Inject,
  Injectable,
  OnApplicationBootstrap,
} from '@nestjs/common';
import { Channel, ChannelModel, ConsumeMessage } from 'amqplib';
import { randomUUID } from 'node:crypto';
import { correlacion } from '../common/correlacion';
import { JsonLogger } from '../common/json.logger';
import { crearValidationPipe } from '../common/validacion';
import { EventoDto } from './dto/evento.dto';
import { EventosService } from './eventos.service';
import {
  CONECTAR_RABBITMQ,
  ConectarRabbitmq,
  RABBITMQ_URL,
} from './rabbitmq.config';

interface Sesion {
  conexion: ChannelModel;
  canal?: Channel;
  consumerTag?: string;
}

@Injectable()
export class ConsumidorEventosService
  implements OnApplicationBootstrap, BeforeApplicationShutdown
{
  private readonly logger = new JsonLogger();
  private readonly validador = crearValidationPipe();
  private readonly pendientes = new Set<Promise<void>>();
  private sesion?: Sesion;
  private conectando?: Promise<void>;
  private temporizador?: NodeJS.Timeout;
  private detenido = false;

  constructor(
    private readonly eventos: EventosService,
    @Inject(RABBITMQ_URL) private readonly url: string | undefined,
    @Inject(CONECTAR_RABBITMQ) private readonly conectar: ConectarRabbitmq,
  ) {}

  onApplicationBootstrap(): void {
    if (!this.url) {
      this.logger.warn(
        'RABBITMQ_URL ausente: consumidor de reportes deshabilitado',
      );
      return;
    }
    // Un error de configuración falla explícitamente, sin exponer credenciales.
    try {
      if (!['amqp:', 'amqps:'].includes(new URL(this.url).protocol))
        throw Error();
    } catch {
      throw new Error('RABBITMQ_URL debe ser una URL amqp o amqps válida');
    }
    this.iniciarConexion();
  }

  private iniciarConexion(): void {
    if (this.detenido || this.conectando || this.sesion) return;
    this.conectando = this.abrirSesion().finally(() => {
      this.conectando = undefined;
      if (!this.sesion) this.reintentar();
    });
  }

  private reintentar(): void {
    if (this.detenido || this.temporizador) return;
    this.temporizador = setTimeout(() => {
      this.temporizador = undefined;
      this.iniciarConexion();
    }, 5000);
    this.temporizador.unref();
  }

  private async abrirSesion(): Promise<void> {
    let sesion: Sesion | undefined;
    try {
      const conexion = await this.conectar(this.url!);
      sesion = { conexion };
      const actual = sesion;
      conexion.on('error', () => this.perderSesion(actual));
      conexion.on('close', () => this.perderSesion(actual));
      if (this.detenido) return;
      this.sesion = sesion;
      const canal = await conexion.createChannel();
      sesion.canal = canal;
      canal.on('error', () => this.perderSesion(actual));
      canal.on('close', () => this.perderSesion(actual));
      if (this.detenido || this.sesion !== sesion) return;
      await canal.prefetch(10);
      // La infraestructura carga la topología. Nunca declaramos colas/exchanges.
      const consumidor = await canal.consume(
        'reportes.eventos',
        (mensaje) => {
          if (mensaje === null) {
            this.perderSesion(actual);
            return;
          }
          const tarea = this.procesar(canal, mensaje, actual);
          this.pendientes.add(tarea);
          void tarea.finally(() => this.pendientes.delete(tarea));
        },
        { noAck: false },
      );
      sesion.consumerTag = consumidor.consumerTag;
      if (this.sesion === sesion && !this.detenido)
        this.logger.log('Consumiendo reportes.eventos con prefetch 10');
    } catch {
      this.logger.warn(
        'No se pudo conectar el consumidor de reportes; se reintentará',
      );
      if (this.sesion === sesion) this.sesion = undefined;
    } finally {
      if (sesion && (this.detenido || this.sesion !== sesion))
        await this.cerrar(sesion);
    }
  }

  private perderSesion(sesion: Sesion): void {
    if (this.detenido || this.sesion !== sesion) return;
    this.sesion = undefined;
    this.logger.warn('Conexión de reportes interrumpida; se reintentará');
    void this.cerrar(sesion).finally(() => this.reintentar());
  }

  private async procesar(
    canal: Channel,
    mensaje: ConsumeMessage,
    sesion: Sesion,
  ): Promise<void> {
    let reencolar = false;
    let exitoso = false;
    let id = mensaje.properties.correlationId || randomUUID();
    try {
      let body: unknown;
      try {
        body = JSON.parse(
          new TextDecoder('utf-8', { fatal: true }).decode(mensaje.content),
        );
      } catch {
        throw new BadRequestException('El mensaje debe ser JSON UTF-8 válido');
      }
      if (
        body &&
        typeof body === 'object' &&
        'correlationId' in body &&
        typeof body.correlationId === 'string'
      )
        id = body.correlationId;
      await correlacion.run(id, async () => {
        const evento: EventoDto = await this.validador.transform(body, {
          type: 'body',
          metatype: EventoDto,
        });
        await this.eventos.procesar(evento);
      });
      exitoso = true;
    } catch (error) {
      reencolar = !(error instanceof BadRequestException);
      correlacion.run(id, () =>
        this.logger.warn(
          reencolar
            ? 'Error al persistir evento: se solicita reentrega'
            : 'Evento inválido: se rechaza hacia la DLQ',
        ),
      );
    }
    try {
      if (exitoso) canal.ack(mensaje);
      // basic.reject cuenta fallos también en RabbitMQ >= 4.3; basic.nack ya no.
      // El límite de entregas y la DLQ pertenecen a la topología del contrato.
      else canal.reject(mensaje, reencolar);
    } catch {
      // Un ACK perdido se recupera por redelivery + eventId idempotente.
      this.perderSesion(sesion);
    }
  }

  private async cerrar(sesion: Sesion): Promise<void> {
    if (sesion.canal) await sesion.canal.close().catch(() => undefined);
    await sesion.conexion.close().catch(() => undefined);
  }

  async beforeApplicationShutdown(): Promise<void> {
    this.detenido = true;
    if (this.temporizador) clearTimeout(this.temporizador);
    await this.conectando;
    const sesion = this.sesion;
    if (!sesion) return;
    if (sesion.consumerTag)
      await sesion.canal?.cancel(sesion.consumerTag).catch(() => undefined);
    let limite: NodeJS.Timeout | undefined;
    await Promise.race([
      Promise.allSettled([...this.pendientes]),
      new Promise<void>((resolve) => {
        limite = setTimeout(resolve, 10000);
      }),
    ]);
    if (limite) clearTimeout(limite);
    this.sesion = undefined;
    await this.cerrar(sesion);
  }
}
