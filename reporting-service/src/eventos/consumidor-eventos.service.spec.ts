import { BadRequestException } from '@nestjs/common';
import { Channel, ChannelModel, ConsumeMessage } from 'amqplib';
import { EventEmitter } from 'node:events';
import { correlacion } from '../common/correlacion';
import { JsonLogger } from '../common/json.logger';
import { eventoEjemplo } from '../../test/fixtures';
import { ConsumidorEventosService } from './consumidor-eventos.service';
import { EventosService } from './eventos.service';

function diferida<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolver) => {
    resolve = resolver;
  });
  return { promise, resolve };
}

function sesionFalsa() {
  const canal = Object.assign(new EventEmitter(), {
    prefetch: jest.fn().mockResolvedValue({}),
    consume: jest.fn().mockResolvedValue({ consumerTag: 'reporting-test' }),
    ack: jest.fn(),
    reject: jest.fn(),
    cancel: jest.fn().mockResolvedValue({}),
    close: jest.fn().mockResolvedValue(undefined),
  });
  const conexion = Object.assign(new EventEmitter(), {
    createChannel: jest.fn().mockResolvedValue(canal),
    close: jest.fn().mockResolvedValue(undefined),
  });
  return { canal, conexion };
}

describe('ConsumidorEventosService', () => {
  const url = 'amqp://usuario:secreto@localhost:5672';
  let sesion: ReturnType<typeof sesionFalsa>;
  let eventos: { procesar: jest.Mock };
  let conectar: jest.Mock<Promise<ChannelModel>, [string]>;
  let consumidor: ConsumidorEventosService;
  const flush = async () => {
    await new Promise(setImmediate);
  };
  const recibir = (body: unknown = eventoEjemplo()) => {
    const mensaje = {
      content: Buffer.isBuffer(body) ? body : Buffer.from(JSON.stringify(body)),
      fields: { deliveryTag: 1 },
      properties: { correlationId: 'correlacion-amqp' },
    } as ConsumeMessage;
    const callback = sesion.canal.consume.mock.calls[0][1] as (
      mensaje: ConsumeMessage | null,
    ) => void;
    callback(mensaje);
    return mensaje;
  };
  const iniciar = async () => {
    consumidor.onApplicationBootstrap();
    await flush();
  };

  beforeEach(() => {
    jest.useFakeTimers({ doNotFake: ['setImmediate'] });
    jest
      .spyOn(JsonLogger.prototype, 'warn')
      .mockImplementation(() => undefined);
    jest.spyOn(JsonLogger.prototype, 'log').mockImplementation(() => undefined);
    sesion = sesionFalsa();
    eventos = {
      procesar: jest.fn().mockResolvedValue({ resultado: 'PROCESADO' }),
    };
    conectar = jest
      .fn()
      .mockResolvedValue(sesion.conexion as unknown as ChannelModel);
    consumidor = new ConsumidorEventosService(
      eventos as unknown as EventosService,
      url,
      conectar,
    );
  });
  afterEach(async () => {
    await consumidor.beforeApplicationShutdown();
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('consume la cola existente con ACK manual y prefetch acotado', async () => {
    await iniciar();
    expect(conectar).toHaveBeenCalledWith(url);
    expect(sesion.canal.prefetch).toHaveBeenCalledWith(10);
    expect(sesion.canal.consume).toHaveBeenCalledWith(
      'reportes.eventos',
      expect.any(Function),
      { noAck: false },
    );
  });

  it('sin URL mantiene disponible el modo HTTP', async () => {
    consumidor = new ConsumidorEventosService(
      eventos as unknown as EventosService,
      undefined,
      conectar,
    );
    await iniciar();
    expect(conectar).not.toHaveBeenCalled();
  });

  it('rechaza una URL mal configurada sin mostrar su valor', () => {
    consumidor = new ConsumidorEventosService(
      eventos as unknown as EventosService,
      'https://usuario:secreto@host',
      conectar,
    );
    expect(() => consumidor.onApplicationBootstrap()).toThrow(
      'RABBITMQ_URL debe ser una URL amqp o amqps válida',
    );
    expect(conectar).not.toHaveBeenCalled();
  });

  it('confirma recién al terminar la persistencia y propaga correlacion', async () => {
    const guardado = diferida<void>();
    const evento = eventoEjemplo({ correlationId: 'cid-p6' });
    eventos.procesar.mockImplementation(async () => {
      expect(correlacion.getStore()).toBe('cid-p6');
      await guardado.promise;
    });
    await iniciar();
    const mensaje = recibir(evento);
    await flush();
    expect(sesion.canal.ack).not.toHaveBeenCalled();
    guardado.resolve();
    await flush();
    expect(sesion.canal.ack).toHaveBeenCalledWith(mensaje);
    expect(sesion.canal.reject).not.toHaveBeenCalled();
  });

  it('confirma duplicados ya persistidos', async () => {
    eventos.procesar.mockResolvedValue({ resultado: 'DUPLICADO' });
    await iniciar();
    const mensaje = recibir();
    await flush();
    expect(sesion.canal.ack).toHaveBeenCalledWith(mensaje);
  });

  it.each([
    ['JSON roto', Buffer.from('{')],
    ['UTF-8 inválido', Buffer.from([0xff])],
    ['null', null],
    ['esquema inválido', { eventId: 'invalido' }],
    ['campos extra', { ...eventoEjemplo(), extra: 'no admitido' }],
    ['versión no soportada', { ...eventoEjemplo(), version: 2 }],
  ])('envía a DLQ %s sin llamar al proyector', async (_, body) => {
    await iniciar();
    const mensaje = recibir(body);
    await flush();
    expect(eventos.procesar).not.toHaveBeenCalled();
    expect(sesion.canal.reject).toHaveBeenCalledWith(mensaje, false);
    expect(sesion.canal.ack).not.toHaveBeenCalled();
  });

  it('envía a DLQ las violaciones de reglas del payload', async () => {
    eventos.procesar.mockRejectedValue(
      new BadRequestException('Falta estadoAnterior'),
    );
    await iniciar();
    const mensaje = recibir();
    await flush();
    expect(sesion.canal.reject).toHaveBeenCalledWith(mensaje, false);
  });

  it('solicita reentrega ante fallos de Mongo sin confirmar', async () => {
    eventos.procesar.mockRejectedValue(new Error('Mongo desconectado'));
    await iniciar();
    const mensaje = recibir();
    await flush();
    expect(sesion.canal.reject).toHaveBeenCalledWith(mensaje, true);
    expect(sesion.canal.ack).not.toHaveBeenCalled();
  });

  it('reconecta después de fallar al iniciar y no filtra secretos al log', async () => {
    conectar.mockRejectedValueOnce(new Error(url));
    await iniciar();
    expect(
      JSON.stringify(jest.mocked(JsonLogger.prototype.warn).mock.calls),
    ).not.toContain('secreto');
    await jest.advanceTimersByTimeAsync(5000);
    expect(conectar).toHaveBeenCalledTimes(2);
    expect(sesion.canal.consume).toHaveBeenCalledTimes(1);
  });

  it.each(['close', 'error', 'cancelación'])(
    'reconecta tras %s sin duplicar reintentos',
    async (tipo) => {
      await iniciar();
      if (tipo === 'cancelación') sesion.canal.consume.mock.calls[0][1](null);
      else {
        sesion.conexion.emit(tipo, new Error('corte'));
        sesion.canal.emit('close');
      }
      await flush();
      await jest.advanceTimersByTimeAsync(5000);
      expect(conectar).toHaveBeenCalledTimes(2);
    },
  );

  it('un ACK fallido cierra la sesión sin rechazar dos veces la entrega', async () => {
    sesion.canal.ack.mockImplementation(() => {
      throw Error('Canal cerrado');
    });
    await iniciar();
    recibir();
    await flush();
    expect(sesion.canal.reject).not.toHaveBeenCalled();
    expect(sesion.conexion.close).toHaveBeenCalled();
  });

  it('al apagar cancela nuevas entregas y espera las pendientes', async () => {
    const guardado = diferida<void>();
    eventos.procesar.mockReturnValue(guardado.promise);
    await iniciar();
    recibir();
    await flush();
    const cierre = consumidor.beforeApplicationShutdown();
    await flush();
    expect(sesion.canal.cancel).toHaveBeenCalledWith('reporting-test');
    expect(sesion.conexion.close).not.toHaveBeenCalled();
    guardado.resolve();
    await cierre;
    expect(sesion.canal.ack).toHaveBeenCalledTimes(1);
    expect(sesion.conexion.close).toHaveBeenCalled();
    await jest.advanceTimersByTimeAsync(10000);
    expect(conectar).toHaveBeenCalledTimes(1);
  });

  it('limita la espera de mensajes bloqueados al apagar', async () => {
    eventos.procesar.mockReturnValue(new Promise(() => undefined));
    await iniciar();
    recibir();
    await flush();
    const cierre = consumidor.beforeApplicationShutdown();
    await jest.advanceTimersByTimeAsync(10000);
    await cierre;
    expect(sesion.conexion.close).toHaveBeenCalled();
  });

  it('cierra una conexión que termina de abrirse después del apagado', async () => {
    const apertura = diferida<ChannelModel>();
    conectar.mockReturnValue(apertura.promise);
    consumidor.onApplicationBootstrap();
    const cierre = consumidor.beforeApplicationShutdown();
    apertura.resolve(sesion.conexion as unknown as ChannelModel);
    await cierre;
    expect(sesion.conexion.createChannel).not.toHaveBeenCalled();
    expect(sesion.conexion.close).toHaveBeenCalled();
    await jest.advanceTimersByTimeAsync(10000);
    expect(conectar).toHaveBeenCalledTimes(1);
  });
});
