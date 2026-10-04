import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getConnectionToken } from '@nestjs/mongoose';
import { Connection } from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import request from 'supertest';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { parse } from 'yaml';
import SwaggerParser from '@apidevtools/swagger-parser';
import { AppModule } from '../src/app.module';
import { configurarHttp } from '../src/common/http.config';
import { configurarSwagger } from '../src/common/swagger.config';
import { EstadoTicket, Prioridad, TipoEvento } from '../src/common/enums';
import { EventoDto } from '../src/eventos/dto/evento.dto';
import { TicketViewRepository } from '../src/proyecciones/ticket-view.repository';
import { ReportesRepository } from '../src/reportes/reportes.repository';
import { eventoEjemplo, eventoEscalado } from './fixtures';

describe('reporting-service HTTP + MongoDB standalone', () => {
  let mongo: MongoMemoryServer;
  let app: INestApplication;
  let conexion: Connection;
  const uriAnterior = process.env.MONGO_URI;
  const nivelAnterior = process.env.LOG_LEVEL;
  beforeAll(async () => {
    process.env.LOG_LEVEL = 'ERROR';
    // La misma topologia standalone de Compose; no es un mock de MongoDB.
    mongo = await MongoMemoryServer.create({
      binary: { version: '7.0.24' },
      instance: { dbName: 'reporting_db' },
    });
    process.env.MONGO_URI = mongo.getUri('reporting_db');
    const modulo = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = modulo.createNestApplication({ logger: false });
    configurarHttp(app);
    configurarSwagger(app);
    await app.init();
    await app.listen(0, '127.0.0.1');
    conexion = app.get<Connection>(getConnectionToken());
  });
  afterAll(async () => {
    await app?.close();
    await mongo?.stop();
    if (uriAnterior === undefined) delete process.env.MONGO_URI;
    else process.env.MONGO_URI = uriAnterior;
    if (nivelAnterior === undefined) delete process.env.LOG_LEVEL;
    else process.env.LOG_LEVEL = nivelAnterior;
  });
  beforeEach(async () => {
    await conexion.collection('ticket_view').deleteMany({});
    await conexion.collection('eventos_procesados').deleteMany({});
  });
  const enviar = (evento: EventoDto) =>
    request(app.getHttpServer()).post('/api/eventos').send(evento).expect(202);
  const listar = () =>
    request(app.getHttpServer()).get('/api/reportes/tickets').expect(200);

  it('health hace ping a MongoDB y propaga la correlacion', async () => {
    const res = await request(app.getHttpServer())
      .get('/health')
      .set('X-Correlation-Id', 'prueba-health')
      .expect(200);
    expect(res.body).toEqual({
      status: 'UP',
      service: 'reporting-service',
      version: '0.1.0',
    });
    expect(res.headers['x-correlation-id']).toBe('prueba-health');
  });
  it('proyecta el snapshot, confirma el evento y detecta el duplicado', async () => {
    const evento = eventoEscalado();
    expect((await enviar(evento)).body.resultado).toBe('PROCESADO');
    expect((await enviar(evento)).body.resultado).toBe('DUPLICADO');
    const res = await listar();
    expect(res.body).toMatchObject({
      totalElements: 1,
      page: 0,
      size: 20,
      totalPages: 1,
    });
    expect(res.body.content[0]).toMatchObject({
      ticketId: evento.payload.ticket.ticketId,
      estado: 'ESCALADO',
      prioridad: 'P1',
      escalado: true,
      fechaResolucion: null,
    });
    expect(res.body.content[0]._id).toBeUndefined();
    expect(
      await conexion
        .collection('eventos_procesados')
        .countDocuments({ estado: 'PROCESADO' }),
    ).toBe(1);
  });
  it('acepta el evento viejo sin sobrescribir una vista mas reciente', async () => {
    const reciente = eventoEscalado();
    reciente.occurredAt = '2026-10-05T14:10:00Z';
    await enviar(reciente);
    const viejo = eventoEjemplo();
    viejo.payload.ticket.ticketId = reciente.payload.ticket.ticketId;
    await enviar(viejo);
    expect((await listar()).body.content[0]).toMatchObject({
      estado: 'ESCALADO',
      ultimoEventoEn: '2026-10-05T14:10:00.000Z',
    });
    expect(
      await conexion.collection('eventos_procesados').countDocuments(),
    ).toBe(2);
  });
  it('procesa duplicados concurrentes una sola vez y conserva el indice unico', async () => {
    const evento = eventoEscalado();
    const respuestas = await Promise.all(
      Array.from({ length: 10 }, () => enviar(evento)),
    );
    expect(
      respuestas.filter((r) => r.body.resultado === 'PROCESADO'),
    ).toHaveLength(1);
    expect(
      respuestas.filter((r) => r.body.resultado === 'DUPLICADO'),
    ).toHaveLength(9);
    expect(await conexion.collection('ticket_view').countDocuments()).toBe(1);
    expect(
      await conexion.collection('eventos_procesados').countDocuments(),
    ).toBe(1);
  });
  it('eventos concurrentes y desordenados conservan la fecha mas reciente', async () => {
    const base = eventoEjemplo();
    const eventos = Array.from({ length: 12 }, (_, i) => ({
      ...base,
      eventId: randomUUID(),
      occurredAt: `2026-10-05T14:${String(i).padStart(2, '0')}:00Z`,
      payload: {
        ticket: { ...base.payload.ticket, titulo: `Snapshot numero ${i}` },
      },
    }));
    await Promise.all(eventos.reverse().map(enviar));
    expect((await listar()).body.content[0]).toMatchObject({
      titulo: 'Snapshot numero 11',
      ultimoEventoEn: '2026-10-05T14:11:00.000Z',
    });
    expect(await conexion.collection('ticket_view').countDocuments()).toBe(1);
  });
  it('permite eventos distintos con el mismo occurredAt', async () => {
    const base = eventoEjemplo();
    await enviar(base);
    const escalado = eventoEscalado();
    escalado.payload.ticket.ticketId = base.payload.ticket.ticketId;
    escalado.occurredAt = base.occurredAt;
    await enviar(escalado);
    expect((await listar()).body.content[0].estado).toBe('ESCALADO');
  });
  it('conserva titulos con $ como texto literal', async () => {
    const evento = eventoEjemplo();
    evento.payload.ticket.titulo = '$titulo literal';
    await enviar(evento);
    expect((await listar()).body.content[0].titulo).toBe('$titulo literal');
  });
  it('aplica el primer snapshot aunque su fecha sea anterior a 1970', async () => {
    const evento = eventoEjemplo({ occurredAt: '1960-01-01T00:00:00Z' });
    evento.payload.ticket.fechaCreacion = evento.occurredAt;
    await enviar(evento);
    expect((await listar()).body.content[0]).toMatchObject({
      titulo: evento.payload.ticket.titulo,
      ultimoEventoEn: '1960-01-01T00:00:00.000Z',
    });
  });
  it('un error de proyeccion deja el evento pendiente y recuperable', async () => {
    const repo = app.get(TicketViewRepository);
    const spy = jest
      .spyOn(repo, 'aplicar')
      .mockRejectedValueOnce(new Error('URI con secreto que no debe salir'));
    const evento = eventoEjemplo();
    const fallo = await request(app.getHttpServer())
      .post('/api/eventos')
      .send(evento)
      .expect(500);
    expect(fallo.body.codigo).toBe('ERROR_INTERNO');
    expect(JSON.stringify(fallo.body)).not.toContain('secreto');
    expect(
      await conexion
        .collection('eventos_procesados')
        .countDocuments({ estado: 'PENDIENTE' }),
    ).toBe(1);
    spy.mockRestore();
    expect((await enviar(evento)).body.resultado).toBe('PROCESADO');
    expect((await listar()).body.totalElements).toBe(1);
  });
  it('si se interrumpe luego del upsert, el reintento conserva la resolucion', async () => {
    const evento = eventoEscalado();
    evento.eventType = TipoEvento.TicketResuelto;
    evento.payload.ticket.estado = EstadoTicket.RESUELTO;
    const aplicar = app.get(TicketViewRepository);
    // Simula una caida entre proyeccion y confirmacion persistiendo la vista.
    const { PROYECTORES } = await import('../src/proyecciones/proyectores');
    await conexion.collection('eventos_procesados').insertOne({
      eventId: evento.eventId,
      evento,
      estado: 'PENDIENTE',
      recibidoEn: new Date(),
    });
    await aplicar.aplicar(PROYECTORES[5].proyectar(evento));
    await enviar(evento);
    expect((await listar()).body.content[0].fechaResolucion).toBe(
      '2026-10-05T14:00:00.000Z',
    );
  });
  it('resuelve, cierra, reabre y vuelve a resolver con una nueva fecha', async () => {
    const evento = eventoEscalado();
    const cambiar = async (estado: EstadoTicket, minutos: number) => {
      const nuevo: EventoDto = {
        ...evento,
        eventId: randomUUID(),
        eventType:
          estado === EstadoTicket.RESUELTO
            ? TipoEvento.TicketResuelto
            : TipoEvento.TicketEstadoCambiado,
        occurredAt: `2026-10-05T14:${minutos}:00Z`,
        payload: {
          ticket: { ...evento.payload.ticket, estado },
          estadoAnterior: EstadoTicket.EN_CURSO,
        },
      };
      await enviar(nuevo);
      return (await listar()).body.content[0];
    };
    expect((await cambiar(EstadoTicket.RESUELTO, 10)).fechaResolucion).toBe(
      '2026-10-05T14:10:00.000Z',
    );
    expect((await cambiar(EstadoTicket.CERRADO, 11)).fechaResolucion).toBe(
      '2026-10-05T14:10:00.000Z',
    );
    expect(
      (await cambiar(EstadoTicket.EN_CURSO, 12)).fechaResolucion,
    ).toBeNull();
    expect((await cambiar(EstadoTicket.RESUELTO, 13)).fechaResolucion).toBe(
      '2026-10-05T14:13:00.000Z',
    );
  });
  it('resumen incluye nulos y excluye resueltos y cerrados de abiertos y vencidos', async () => {
    const sinClasificar = eventoEjemplo();
    await enviar(sinClasificar);
    const vencido = eventoEscalado();
    vencido.payload.ticket.fechaLimiteSla = '2020-01-01T00:00:00Z';
    await enviar(vencido);
    for (const estado of [EstadoTicket.RESUELTO, EstadoTicket.CERRADO]) {
      const evento = eventoEscalado();
      evento.eventType = TipoEvento.TicketEstadoCambiado;
      evento.payload.ticket.estado = estado;
      evento.payload.ticket.fechaLimiteSla = '2020-01-01T00:00:00Z';
      await enviar(evento);
    }
    const res = await request(app.getHttpServer())
      .get('/api/reportes/resumen')
      .expect(200);
    expect(res.body).toMatchObject({
      total: 4,
      abiertos: 2,
      escalados: 1,
      slaVencidos: 1,
      porPrioridad: { P1: 3, SIN_PRIORIDAD: 1 },
      porCategoria: { INCIDENTE: 3, SIN_CATEGORIA: 1 },
      porEstado: { NUEVO: 1, ESCALADO: 1, RESUELTO: 1, CERRADO: 1 },
    });
  });
  it('SLA calcula cumplimiento inclusivo en el limite y null sin resueltos', async () => {
    for (const occurredAt of ['2026-10-05T15:00:00Z', '2026-10-05T15:00:01Z']) {
      const evento = eventoEscalado();
      evento.eventType = TipoEvento.TicketResuelto;
      evento.occurredAt = occurredAt;
      evento.payload.ticket.estado = EstadoTicket.RESUELTO;
      await enviar(evento);
    }
    const vencido = eventoEscalado();
    vencido.payload.ticket.fechaLimiteSla = '2020-01-01T00:00:00Z';
    await enviar(vencido);
    const res = await request(app.getHttpServer())
      .get('/api/reportes/sla')
      .expect(200);
    expect(res.body.vencidos).toHaveLength(1);
    expect(res.body.vencidos[0].ticketId).toBe(vencido.payload.ticket.ticketId);
    expect(res.body.cumplimientoPorPrioridad.P1).toEqual({
      resueltos: 2,
      enTermino: 1,
      porcentaje: 50,
    });
    expect(res.body.cumplimientoPorPrioridad.P2.porcentaje).toBeNull();
  });
  it('vencimiento igual a ahora todavia no cuenta como vencido', async () => {
    const evento = eventoEscalado();
    await enviar(evento);
    const repo = app.get(ReportesRepository);
    expect(
      (await repo.sla(new Date('2026-10-05T15:00:00Z'))).vencidos,
    ).toHaveLength(0);
    expect(
      (await repo.sla(new Date('2026-10-05T15:00:00.001Z'))).vencidos,
    ).toHaveLength(1);
  });
  it('filtra y pagina sin duplicar tickets entre paginas', async () => {
    const p1 = Array.from({ length: 3 }, eventoEscalado);
    await Promise.all(p1.map(enviar));
    await enviar(eventoEjemplo());
    const pagina = await request(app.getHttpServer())
      .get('/api/reportes/tickets?prioridad=P1&estado=ESCALADO&page=1&size=2')
      .expect(200);
    expect(pagina.body).toMatchObject({
      totalElements: 3,
      totalPages: 2,
      page: 1,
      size: 2,
    });
    expect(pagina.body.content).toHaveLength(1);
    const primera = await request(app.getHttpServer())
      .get('/api/reportes/tickets?prioridad=P1&estado=ESCALADO&page=0&size=2')
      .expect(200);
    expect(
      primera.body.content.map((t: { ticketId: string }) => t.ticketId),
    ).not.toContain(pagina.body.content[0].ticketId);
  });
  it.each([
    'size=0',
    'size=101',
    'size=1.5',
    'page=-1',
    'page=abc',
    'page=',
    'page=1e2',
    'prioridad=P9',
    'estado=INVALIDO',
    'foo=bar',
  ])('rechaza filtros o paginacion invalidos: %s', async (query) => {
    const res = await request(app.getHttpServer())
      .get(`/api/reportes/tickets?${query}`)
      .set('X-Correlation-Id', 'validacion')
      .expect(400);
    expect(res.body).toMatchObject({
      codigo: 'VALIDACION',
      path: '/api/reportes/tickets',
      correlationId: 'validacion',
    });
    expect(res.body.detalles.length).toBeGreaterThan(0);
  });
  it('acepta filtros vacios y devuelve pagina vacia con totales cero', async () => {
    const res = await request(app.getHttpServer())
      .get('/api/reportes/tickets?prioridad=&estado=')
      .expect(200);
    expect(res.body).toEqual({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
  });
  it.each([
    (e: any) => {
      e.version = 2;
    },
    (e: any) => {
      e.eventType = 'Inventado';
    },
    (e: any) => {
      e.payload.ticket.descripcion = 'dato sensible';
    },
    (e: any) => {
      delete e.payload.ticket.prioridad;
    },
    (e: any) => {
      e.payload = null;
    },
    (e: any) => {
      e.payload.ticket.fechaCreacion = '2026-02-30T00:00:00Z';
    },
    (e: any) => {
      e.occurredAt = '2026-10-05T14:00:00-03:00';
    },
    (e: any) => {
      e.eventId = 'no-es-uuid';
    },
    (e: any) => {
      e.payload.ticket.estado = 'INVENTADO';
    },
    (e: any) => {
      e.payload.ticket.prioridad = 'P9';
    },
  ])('rechaza eventos invalidos antes de persistir %#', async (modificar) => {
    const evento = eventoEjemplo();
    modificar(evento);
    const res = await request(app.getHttpServer())
      .post('/api/eventos')
      .send(evento)
      .expect(400);
    expect(res.body.codigo).toBe('VALIDACION');
    expect(
      await conexion.collection('eventos_procesados').countDocuments(),
    ).toBe(0);
  });
  it('rechaza payload sin estadoAnterior o motivo cuando corresponde', async () => {
    const evento = eventoEscalado();
    delete evento.payload.estadoAnterior;
    await request(app.getHttpServer())
      .post('/api/eventos')
      .send(evento)
      .expect(400);
  });
  it('JSON malformado responde 400 y las rutas inexistentes 404 con formato comun', async () => {
    const json = await request(app.getHttpServer())
      .post('/api/eventos')
      .set('Content-Type', 'application/json')
      .send('{')
      .expect(400);
    expect(json.body.codigo).toBe('VALIDACION');
    const ruta = await request(app.getHttpServer())
      .get('/no-existe')
      .expect(404);
    expect(ruta.body.codigo).toBe('NO_ENCONTRADO');
  });
  it('Swagger publica los endpoints y el contrato OpenAPI es valido', async () => {
    const runtime = await request(app.getHttpServer())
      .get('/api-docs-json')
      .expect(200);
    const contrato = parse(
      readFileSync(
        resolve(__dirname, '../../contracts/reporting-service.yaml'),
        'utf8',
      ),
    );
    await SwaggerParser.validate(contrato);
    expect(Object.keys(runtime.body.paths).sort()).toEqual(
      Object.keys(contrato.paths).sort(),
    );
    for (const schema of [
      'EventoDto',
      'TicketSnapshotDto',
      'TicketViewDto',
      'ResumenDto',
      'SlaDto',
      'PaginaTicketsDto',
    ]) {
      expect(runtime.body.components.schemas[schema].required.sort()).toEqual(
        contrato.components.schemas[schema].required.sort(),
      );
    }
    expect(
      runtime.body.components.schemas.EventoDto.properties.eventType.enum,
    ).toEqual(Object.values(TipoEvento));
    expect(
      runtime.body.components.schemas.TicketSnapshotDto.properties
        .agenteAsignadoId.type,
    ).toBe('string');
    expect(
      runtime.body.components.schemas.TicketViewDto.properties.fechaResolucion
        .type,
    ).toBe('string');
    expect(
      runtime.body.components.schemas.CumplimientoDto.properties.porcentaje
        .type,
    ).toBe('number');
    expect(
      runtime.body.paths['/api/eventos'].post.responses['400'].content[
        'application/json'
      ].schema.$ref,
    ).toContain('ErrorDto');
    await request(app.getHttpServer()).get('/api-docs/').expect(200);
  });
});
