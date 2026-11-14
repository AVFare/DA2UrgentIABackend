import { INestApplication } from '@nestjs/common';
import { getConnectionToken } from '@nestjs/mongoose';
import { Test } from '@nestjs/testing';
import { ChannelModel, ConfirmChannel, connect } from 'amqplib';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { Connection } from 'mongoose';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { EstadoTicket, TipoEvento } from '../src/common/enums';
import { configurarHttp } from '../src/common/http.config';
import { EventoDto } from '../src/eventos/dto/evento.dto';
import { EventosService } from '../src/eventos/eventos.service';
import { EventoProcesadoRepository } from '../src/eventos/evento-procesado.repository';
import { RABBITMQ_URL } from '../src/eventos/rabbitmq.config';
import { eventoEjemplo, eventoEscalado } from './fixtures';

interface Consumidor {
  queue: { name: string };
  channel_details: { connection_name: string };
}

async function esperar<T>(
  consultar: () => Promise<T>,
  listo: (valor: T) => boolean,
): Promise<T> {
  const limite = Date.now() + 20000;
  while (Date.now() < limite) {
    const valor = await consultar();
    if (listo(valor)) return valor;
    await new Promise((resolver) => setTimeout(resolver, 100));
  }
  throw new Error('La condición esperada no se cumplió en 20 segundos');
}

describe('reporting por RabbitMQ real + MongoDB standalone', () => {
  const vhost = `reporting-test-${randomUUID()}`;
  let creado = false;
  let broker: URL;
  let administracion: URL;
  let auth: string;
  let mongo: MongoMemoryServer;
  let app: INestApplication;
  let conexion: Connection;
  let productor: ChannelModel;
  let canal: ConfirmChannel;
  const uriAnterior = process.env.MONGO_URI;
  const nivelAnterior = process.env.LOG_LEVEL;

  async function api(ruta: string, method = 'GET', body?: unknown) {
    const respuesta = await fetch(new URL(`/api/${ruta}`, administracion), {
      method,
      headers: { Authorization: auth, 'content-type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(10000),
    });
    if (!respuesta.ok)
      throw new Error(`RabbitMQ management: HTTP ${respuesta.status}`);
    const contenido = await respuesta.text();
    return contenido ? JSON.parse(contenido) : undefined;
  }

  async function esperarConsumidor(conexionAnterior?: string) {
    return esperar(
      () => api(`consumers/${encodeURIComponent(vhost)}`),
      (consumidores: Consumidor[]) =>
        consumidores.some(
          (c) =>
            c.queue.name === 'reportes.eventos' &&
            c.channel_details.connection_name !== conexionAnterior,
        ),
    );
  }

  const publicar = async (evento: EventoDto | Buffer) => {
    canal.publish(
      'urgentia.eventos',
      'ticket.prueba',
      Buffer.isBuffer(evento) ? evento : Buffer.from(JSON.stringify(evento)),
      {
        persistent: true,
        contentType: 'application/json',
        correlationId: Buffer.isBuffer(evento)
          ? 'cid-invalido'
          : evento.correlationId,
      },
    );
    await canal.waitForConfirms();
  };
  const procesado = (eventId: string) =>
    esperar(
      () => conexion.collection('eventos_procesados').findOne({ eventId }),
      (evento) => evento?.estado === 'PROCESADO',
    );
  const siguienteDlq = () =>
    esperar(
      () => canal.get('reportes.eventos.dlq', { noAck: true }),
      (mensaje) => mensaje !== false,
    );

  beforeAll(async () => {
    if (!process.env.RABBITMQ_URL)
      throw new Error(
        'test:rabbitmq requiere RABBITMQ_URL y acceso administrador al broker de desarrollo',
      );
    broker = new URL(process.env.RABBITMQ_URL);
    administracion = new URL(
      process.env.RABBITMQ_MANAGEMENT_URL ?? `http://${broker.hostname}:15672`,
    );
    auth = `Basic ${Buffer.from(`${decodeURIComponent(broker.username)}:${decodeURIComponent(broker.password)}`).toString('base64')}`;
    await api(`vhosts/${encodeURIComponent(vhost)}`, 'PUT', {});
    creado = true;
    await api(
      `permissions/${encodeURIComponent(vhost)}/${encodeURIComponent(decodeURIComponent(broker.username))}`,
      'PUT',
      {
        configure: '.*',
        write: '.*',
        read: '.*',
      },
    );
    const definiciones = JSON.parse(
      readFileSync(
        resolve(
          __dirname,
          '../../contracts/messaging/rabbitmq-definitions.json',
        ),
        'utf8',
      ),
    );
    // Carga la topología compartida SOLO en el vhost temporal, nunca en el del equipo.
    await api('definitions', 'POST', {
      vhosts: [{ name: vhost }],
      ...Object.fromEntries(
        ['exchanges', 'queues', 'bindings'].map((tipo) => [
          tipo,
          definiciones[tipo].map((elemento: object) => ({
            ...elemento,
            vhost,
          })),
        ]),
      ),
    });
    broker.pathname = `/${encodeURIComponent(vhost)}`;
    process.env.LOG_LEVEL = 'ERROR';
    mongo = await MongoMemoryServer.create({
      binary: { version: '7.0.24' },
      instance: { dbName: 'reporting_db' },
    });
    process.env.MONGO_URI = mongo.getUri('reporting_db');
    const modulo = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(RABBITMQ_URL)
      .useValue(broker.toString())
      .compile();
    app = modulo.createNestApplication({ logger: false });
    configurarHttp(app);
    await app.init();
    conexion = app.get<Connection>(getConnectionToken());
    productor = await connect(broker.toString());
    productor.on('error', () => undefined);
    canal = await productor.createConfirmChannel();
    canal.on('error', () => undefined);
    await esperarConsumidor();
  });

  afterAll(async () => {
    jest.restoreAllMocks();
    try {
      await app?.close();
      await productor?.close();
      await mongo?.stop();
    } finally {
      if (creado) await api(`vhosts/${encodeURIComponent(vhost)}`, 'DELETE');
      if (uriAnterior === undefined) delete process.env.MONGO_URI;
      else process.env.MONGO_URI = uriAnterior;
      if (nivelAnterior === undefined) delete process.env.LOG_LEVEL;
      else process.env.LOG_LEVEL = nivelAnterior;
    }
  });

  it.each(Object.values(TipoEvento))(
    'proyecta %s y lo publica en la API de reportes',
    async (tipo) => {
      const evento =
        tipo === TipoEvento.TicketEscalado
          ? eventoEscalado()
          : eventoEjemplo({ eventType: tipo });
      if (tipo === TipoEvento.TicketResuelto) {
        evento.payload.ticket.estado = EstadoTicket.RESUELTO;
        evento.payload.estadoAnterior = EstadoTicket.EN_CURSO;
      }
      if (tipo === TipoEvento.TicketEstadoCambiado) {
        evento.payload.ticket.estado = EstadoTicket.EN_CURSO;
        evento.payload.estadoAnterior = EstadoTicket.ASIGNADO;
      }
      await publicar(evento);
      await procesado(evento.eventId);
      const respuesta = await request(app.getHttpServer())
        .get('/api/reportes/tickets?size=100')
        .expect(200);
      expect(respuesta.body.content).toContainEqual(
        expect.objectContaining({
          ticketId: evento.payload.ticket.ticketId,
          estado: evento.payload.ticket.estado,
        }),
      );
    },
  );

  it('tolera duplicados concurrentes y no pisa el snapshot más reciente', async () => {
    const reciente = eventoEscalado();
    reciente.occurredAt = '2026-10-05T16:00:00Z';
    await Promise.all(Array.from({ length: 12 }, () => publicar(reciente)));
    await procesado(reciente.eventId);
    const anterior = eventoEjemplo({
      payload: {
        ticket: { ...reciente.payload.ticket, estado: EstadoTicket.NUEVO },
      },
    });
    await publicar(anterior);
    await procesado(anterior.eventId);
    expect(
      await conexion
        .collection('eventos_procesados')
        .countDocuments({ eventId: reciente.eventId }),
    ).toBe(1);
    const vista = await conexion
      .collection('ticket_view')
      .findOne({ ticketId: reciente.payload.ticket.ticketId });
    expect(vista?.estado).toBe(EstadoTicket.ESCALADO);
    expect(vista?.ultimoEventoEn).toEqual(new Date(reciente.occurredAt));
  });

  it.each(['json', 'esquema', 'regla de negocio'])(
    'manda a DLQ un mensaje inválido: %s',
    async (caso) => {
      const evento = eventoEscalado();
      if (caso === 'regla de negocio') delete evento.payload.motivo;
      const contenido =
        caso === 'json'
          ? Buffer.from('{')
          : caso === 'esquema'
            ? Buffer.from(JSON.stringify({ ...evento, version: 999 }))
            : evento;
      await publicar(contenido);
      const mensaje = await siguienteDlq();
      expect(mensaje).not.toBe(false);
      if (mensaje)
        expect(mensaje.properties.headers?.['x-first-death-reason']).toBe(
          'rejected',
        );
      expect(
        await conexion
          .collection('eventos_procesados')
          .countDocuments({ eventId: evento.eventId }),
      ).toBe(0);
    },
  );

  it('recupera un fallo entre proyección y confirmación con el evento PENDIENTE', async () => {
    const repositorio = app.get(EventoProcesadoRepository);
    const completar = jest.spyOn(repositorio, 'completar');
    completar.mockRejectedValueOnce(
      new Error('Mongo temporalmente inaccesible'),
    );
    const evento = eventoEjemplo();
    try {
      await publicar(evento);
      await procesado(evento.eventId);
      expect(completar).toHaveBeenCalledTimes(2);
      expect(
        await conexion
          .collection('ticket_view')
          .countDocuments({ ticketId: evento.payload.ticket.ticketId }),
      ).toBe(1);
    } finally {
      completar.mockRestore();
    }
  });

  it('respeta x-delivery-limit=5 y manda a DLQ un fallo persistente', async () => {
    const servicio = app.get(EventosService);
    const procesar = jest
      .spyOn(servicio, 'procesar')
      .mockRejectedValue(new Error('Fallo persistente'));
    try {
      await publicar(eventoEjemplo());
      const mensaje = await siguienteDlq();
      expect(mensaje).not.toBe(false);
      if (mensaje)
        expect(mensaje.properties.headers?.['x-first-death-reason']).toBe(
          'delivery_limit',
        );
      // Primera entrega + cinco reentregas, administradas por la cola quorum.
      expect(procesar).toHaveBeenCalledTimes(6);
    } finally {
      procesar.mockRestore();
    }
  });

  it('reconecta después de que el broker cierre su conexión y sigue proyectando', async () => {
    const consumidores = await esperarConsumidor();
    const consumidor = consumidores.find(
      (c) => c.queue.name === 'reportes.eventos',
    );
    if (!consumidor) throw new Error('No apareció el consumidor de reporting');
    await api(
      `connections/${encodeURIComponent(consumidor.channel_details.connection_name)}`,
      'DELETE',
    );
    await esperarConsumidor(consumidor.channel_details.connection_name);
    const evento = eventoEjemplo();
    await publicar(evento);
    await procesado(evento.eventId);
  });
});
