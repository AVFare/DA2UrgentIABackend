// Pruebas de la mensajeria sobre el Compose levantado (LLM_PROVIDER=mock, CLASIFICACION_MODO=async).
// Dos casos sin fallas, que se pueden correr por separado:
//
//   contrato       Contrato de los consumidores, sin pasar por ticket-service. Publica a mano, por la API
//                  de administracion de RabbitMQ, un TicketEscalado y un ClasificacionSolicitada validos
//                  segun contracts/events/ y contracts/messaging/, para un ticket que no existe.
//                  Verifica (por ticketId):
//                    1. notification-service registra la notificacion a GRUPO:GUARDIA.
//                    2. reporting-service proyecta el ticket como ESCALADO.
//                    3. classification-service guarda la clasificacion y publica ClasificacionGenerada, que
//                       consume ticket-service (como el ticket no existe, la confirma sin efectos).
//                    4. Las colas de trabajo se vacian y las DLQ quedan vacias.
//                  Si punta-a-punta falla y contrato pasa, el problema esta del lado de ticket-service.
//
//   punta-a-punta  Camino feliz completo desde el gateway. Crea un ticket critico con POST /api/tickets y
//                  sigue cada paso, siempre con el X-Correlation-Id del alta:
//                    1. Crear: el alta responde 201 en PENDIENTE_CLASIFICACION.
//                    2. Cola: ticket-service publica ClasificacionSolicitada (desde el Outbox) y TicketCreado.
//                    3. Clasificar: classification-service publica ClasificacionGenerada con resultado
//                       CLASIFICADO y guarda esa misma clasificacion.
//                    4. Escalar: ticket-service la aplica: el ticket queda P1 y ESCALADO, y publica
//                       TicketClasificado y TicketEscalado, en ese orden.
//                    5. Notificar: notification-service registra la notificacion a GRUPO:GUARDIA.
//                    6. Reportar: reporting-service proyecta el ticket como ESCALADO.
//                    7. Las colas de trabajo se vacian y las DLQ quedan vacias.
//                  Para ver los mensajes sin quitarselos a los consumidores, crea una cola temporal propia
//                  enlazada a urgentia.clasificacion y a urgentia.eventos, y la borra al final.
//
// Uso, desde la raiz del repo y con el Compose levantado:
//   node infra/ci/verificar-mensajeria.cjs                  (los dos, en orden)
//   node infra/ci/verificar-mensajeria.cjs punta-a-punta    (solo ese)
// Toma RABBITMQ_USER y RABBITMQ_PASSWORD del entorno o, si no estan, del .env de la raiz.
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');
const { setTimeout: esperar } = require('node:timers/promises');

const GATEWAY = process.env.GATEWAY_URL || 'http://localhost:8080';
const RABBIT_API = process.env.RABBITMQ_API_URL || 'http://localhost:15672/api';
const VHOST = '%2F';
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS || 60000);
const INTERVALO_MS = 1000;

const COLAS_DE_TRABAJO = ['notificaciones.eventos', 'reportes.eventos', 'clasificacion.solicitudes', 'tickets.clasificaciones'];
const DLQS = [
  'notificaciones.eventos.dlq',
  'reportes.eventos.dlq',
  'clasificacion.solicitudes.dlq',
  'tickets.clasificaciones.dlq',
];
// Sofia Solicitante, de los usuarios semilla de user-service (DatosSemilla).
const SOLICITANTE_SEMILLA = 'b1c2d3e4-0000-4000-8000-000000000004';
const COLA_ESPIA = 'ci.mensajeria.punta-a-punta';
const MENSAJES_DEL_CAMINO = ['ClasificacionSolicitada', 'ClasificacionGenerada', 'TicketCreado', 'TicketClasificado', 'TicketEscalado'];

// ---------- Utilidades ----------

function credencialesRabbit() {
  const delArchivo = {};
  if (fs.existsSync('.env')) {
    for (const linea of fs.readFileSync('.env', 'utf8').split(/\r?\n/)) {
      const campo = /^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/.exec(linea);
      if (campo) delArchivo[campo[1]] = campo[2];
    }
  }
  const usuario = process.env.RABBITMQ_USER || delArchivo.RABBITMQ_USER;
  const clave = process.env.RABBITMQ_PASSWORD || delArchivo.RABBITMQ_PASSWORD;
  assert.ok(usuario && clave, 'Faltan RABBITMQ_USER y RABBITMQ_PASSWORD (entorno o .env)');
  return 'Basic ' + Buffer.from(`${usuario}:${clave}`).toString('base64');
}

// Devuelve { status, headers, body }. status: el codigo esperado o una lista de codigos aceptados.
async function pedirCompleto(url, { metodo = 'GET', headers = {}, body, status = 200 } = {}) {
  const aceptados = [].concat(status);
  const respuesta = await fetch(url, {
    method: metodo,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(10000),
  });
  const texto = await respuesta.text();
  if (!aceptados.includes(respuesta.status)) {
    throw new Error(`${metodo} ${url}: HTTP ${respuesta.status} (se esperaba ${aceptados.join(' o ')}) ${texto.slice(0, 300)}`);
  }
  return { status: respuesta.status, headers: respuesta.headers, body: texto ? JSON.parse(texto) : null };
}

async function pedir(url, opciones) {
  return (await pedirCompleto(url, opciones)).body;
}

// Reintenta condicion() hasta que devuelva { ok: true, valor } o se venza el plazo.
async function esperarQue(descripcion, condicion) {
  const limite = Date.now() + TIMEOUT_MS;
  let ultimo;
  do {
    try {
      ultimo = await condicion();
    } catch (error) {
      ultimo = { ok: false, detalle: error.message };
    }
    if (ultimo.ok) {
      console.log(`OK  ${descripcion}`);
      return ultimo.valor;
    }
    await esperar(INTERVALO_MS);
  } while (Date.now() < limite);
  throw new Error(`No se cumplio en ${TIMEOUT_MS / 1000} s: ${descripcion}. Ultimo estado: ${JSON.stringify(ultimo.detalle)}`);
}

function crearCaso(prueba) {
  return {
    ticketId: randomUUID(),
    correlationId: `p1-mensajeria-${prueba}-${randomUUID()}`,
    titulo: `Caida de produccion (prueba de mensajeria: ${prueba})`,
    descripcion: 'Produccion caida: no funciona el login y todos los usuarios estan bloqueados.',
  };
}

// ---------- Contexto compartido por los casos ----------

function crearContexto() {
  const authRabbit = credencialesRabbit();
  const rabbit = (ruta, opciones = {}) =>
    pedir(`${RABBIT_API}${ruta}`, { ...opciones, headers: { Authorization: authRabbit } });
  const mensajesEn = async (cola) => (await rabbit(`/queues/${VHOST}/${encodeURIComponent(cola)}`)).messages ?? 0;
  const tokens = {};

  async function login(email, password) {
    const sesion = await esperarQue(`login de ${email}`, async () => ({
      ok: true,
      valor: await pedir(`${GATEWAY}/api/auth/login`, { metodo: 'POST', body: { email, password } }),
    }));
    return sesion.accessToken;
  }

  const gateway = (ruta, token, correlationId) =>
    pedir(`${GATEWAY}${ruta}`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Correlation-Id': correlationId },
    });

  async function esperarNotificacion(caso) {
    await esperarQue('notification-service registro la notificacion a GRUPO:GUARDIA', async () => {
      const pagina = await gateway(`/api/notificaciones?ticketId=${caso.ticketId}&size=100`, tokens.agente, caso.correlationId);
      const ok = pagina.content.some(
        (n) => n.ticketId === caso.ticketId && n.tipoEvento === 'TicketEscalado' && n.destinatario === 'GRUPO:GUARDIA',
      );
      return { ok, detalle: pagina.content };
    });
  }

  async function esperarProyeccion(caso) {
    await esperarQue('reporting-service proyecto el ticket como ESCALADO', async () => {
      const pagina = await gateway('/api/reportes/tickets?prioridad=P1&estado=ESCALADO&size=100', tokens.agente, caso.correlationId);
      const vista = pagina.content.find((v) => v.ticketId === caso.ticketId);
      return { ok: vista?.estado === 'ESCALADO' && vista?.escalado === true, detalle: vista ?? 'sin vista' };
    });
  }

  async function clasificacionesGuardadas(caso) {
    return (await gateway(`/api/clasificaciones?ticketId=${caso.ticketId}`, tokens.admin, caso.correlationId)).content;
  }

  async function esperarColasYDlqsVacias() {
    await esperarQue(`las colas de trabajo quedaron vacias (${COLAS_DE_TRABAJO.join(', ')})`, async () => {
      const cuentas = Object.fromEntries(await Promise.all(COLAS_DE_TRABAJO.map(async (c) => [c, await mensajesEn(c)])));
      return { ok: Object.values(cuentas).every((n) => n === 0), detalle: cuentas };
    });
    const enDlq = Object.fromEntries(await Promise.all(DLQS.map(async (c) => [c, await mensajesEn(c)])));
    assert.ok(Object.values(enDlq).every((n) => n === 0), `Hay mensajes en las DLQ: ${JSON.stringify(enDlq)}`);
    console.log('OK  las DLQ estan vacias');
  }

  return {
    rabbit, mensajesEn, tokens, login, gateway,
    esperarNotificacion, esperarProyeccion, clasificacionesGuardadas, esperarColasYDlqsVacias,
  };
}

// ---------- Caso 1: contrato de los consumidores (publica a mano) ----------

async function casoContrato(ctx) {
  const caso = crearCaso('contrato');
  console.log(`Caso: ticketId=${caso.ticketId} correlationId=${caso.correlationId}`);

  async function publicar(exchange, sobre) {
    const resultado = await ctx.rabbit(`/exchanges/${VHOST}/${encodeURIComponent(exchange)}/publish`, {
      metodo: 'POST',
      body: {
        routing_key: sobre.eventType,
        payload: JSON.stringify(sobre),
        payload_encoding: 'string',
        // Propiedades AMQP de contracts/messaging/README.md (reglas para quien publica).
        properties: {
          content_type: 'application/json',
          content_encoding: 'utf-8',
          delivery_mode: 2,
          message_id: sobre.eventId,
          correlation_id: sobre.correlationId,
          type: sobre.eventType,
          app_id: sobre.source,
          timestamp: Math.floor(Date.parse(sobre.occurredAt) / 1000),
        },
      },
    });
    assert.equal(resultado.routed, true, `${sobre.eventType} no llego a ninguna cola de ${exchange}`);
    console.log(`OK  ${sobre.eventType} publicado en ${exchange}`);
  }

  const ahora = new Date();
  const fecha = (d) => d.toISOString().replace(/\.\d{3}Z$/, 'Z');
  const sobre = (eventType, payload) => ({
    eventId: randomUUID(),
    eventType,
    version: 1,
    occurredAt: fecha(ahora),
    correlationId: caso.correlationId,
    source: 'ticket-service',
    payload,
  });
  await publicar(
    'urgentia.eventos',
    sobre('TicketEscalado', {
      ticket: {
        ticketId: caso.ticketId,
        titulo: caso.titulo,
        estado: 'ESCALADO',
        prioridad: 'P1',
        categoria: 'INCIDENTE',
        moduloAfectado: 'AUTENTICACION',
        solicitanteId: SOLICITANTE_SEMILLA,
        agenteAsignadoId: null,
        fechaCreacion: fecha(ahora),
        fechaLimiteSla: fecha(new Date(ahora.getTime() + 60 * 60 * 1000)),
      },
      estadoAnterior: 'CLASIFICADO',
      motivo: 'Prioridad P1',
    }),
  );
  await publicar(
    'urgentia.clasificacion',
    sobre('ClasificacionSolicitada', {
      ticketId: caso.ticketId,
      titulo: caso.titulo,
      descripcion: caso.descripcion,
    }),
  );

  await ctx.esperarNotificacion(caso);
  await ctx.esperarProyeccion(caso);
  await esperarQue('classification-service guardo la clasificacion', async () => {
    const guardadas = await ctx.clasificacionesGuardadas(caso);
    return { ok: guardadas.length > 0, detalle: guardadas };
  });
  await ctx.esperarColasYDlqsVacias();
}

// ---------- Caso 2: punta a punta desde el gateway ----------

async function casoPuntaAPunta(ctx) {
  const caso = crearCaso('punta-a-punta');
  const cola = encodeURIComponent(COLA_ESPIA);

  // Cola espia: recibe una copia de cada mensaje; los consumidores reciben los suyos igual.
  await ctx.rabbit(`/queues/${VHOST}/${cola}`, { metodo: 'DELETE', status: [204, 404] });
  await ctx.rabbit(`/queues/${VHOST}/${cola}`, {
    metodo: 'PUT',
    body: { durable: true, auto_delete: false, arguments: { 'x-queue-type': 'classic' } },
    status: [201, 204],
  });
  const enlaces = [
    ['urgentia.clasificacion', 'ClasificacionSolicitada'],
    ['urgentia.clasificacion', 'ClasificacionGenerada'],
    ['urgentia.eventos', '#'],
  ];
  for (const [exchange, routingKey] of enlaces) {
    await ctx.rabbit(`/bindings/${VHOST}/e/${encodeURIComponent(exchange)}/q/${cola}`, {
      metodo: 'POST',
      body: { routing_key: routingKey },
      status: [201],
    });
  }
  console.log(`OK  cola temporal ${COLA_ESPIA} enlazada a urgentia.clasificacion y urgentia.eventos`);

  try {
    // 1. Crear
    const alta = await pedirCompleto(`${GATEWAY}/api/tickets`, {
      metodo: 'POST',
      headers: { Authorization: `Bearer ${ctx.tokens.solicitante}`, 'X-Correlation-Id': caso.correlationId },
      body: { titulo: caso.titulo, descripcion: caso.descripcion },
      status: 201,
    });
    const creado = alta.body;
    assert.ok(creado.id, 'El alta no devolvio el id del ticket');
    assert.equal(creado.estado, 'PENDIENTE_CLASIFICACION', 'El alta tiene que responder PENDIENTE_CLASIFICACION (revisar CLASIFICACION_MODO=async)');
    assert.ok(creado.prioridad == null, 'Sin clasificar, el ticket no tiene que tener prioridad');
    assert.equal(alta.headers.get('x-correlation-id'), caso.correlationId, 'El gateway no devolvio el X-Correlation-Id del alta');
    caso.ticketId = creado.id;
    console.log(`OK  1. Crear: ticket ${caso.ticketId} creado por el gateway en PENDIENTE_CLASIFICACION (correlationId=${caso.correlationId})`);

    // Junta, en orden de llegada, los mensajes del caso que pasan por la cola espia.
    const recibidos = [];
    async function esperarMensajes(descripcion, tipos) {
      return esperarQue(descripcion, async () => {
        const lote = await ctx.rabbit(`/queues/${VHOST}/${cola}/get`, {
          metodo: 'POST',
          body: { count: 100, ackmode: 'ack_requeue_false', encoding: 'auto' },
        });
        for (const mensaje of lote) {
          const sobre = JSON.parse(mensaje.payload);
          if (sobre.correlationId === caso.correlationId) recibidos.push({ sobre, propiedades: mensaje.properties });
        }
        const tipo = (t) => recibidos.find((m) => m.sobre.eventType === t);
        return {
          ok: tipos.every(tipo),
          valor: Object.fromEntries(tipos.map((t) => [t, tipo(t)])),
          detalle: recibidos.map((m) => m.sobre.eventType),
        };
      });
    }

    // 2. Cola
    const deLaCola = await esperarMensajes('2. Cola: ticket-service publico ClasificacionSolicitada y TicketCreado', [
      'ClasificacionSolicitada',
      'TicketCreado',
    ]);
    const solicitada = deLaCola.ClasificacionSolicitada.sobre;
    assert.equal(solicitada.source, 'ticket-service');
    assert.deepEqual(
      solicitada.payload,
      { ticketId: caso.ticketId, titulo: caso.titulo, descripcion: caso.descripcion },
      'ClasificacionSolicitada no lleva el ticketId, el titulo y la descripcion del alta',
    );
    const creadoEvento = deLaCola.TicketCreado.sobre;
    assert.equal(creadoEvento.payload.ticket.ticketId, caso.ticketId);
    assert.ok(creadoEvento.payload.ticket.prioridad == null, 'TicketCreado no tiene que traer prioridad');

    // 3. Clasificar
    const generadaMensaje = (await esperarMensajes('3. Clasificar: classification-service publico ClasificacionGenerada', [
      'ClasificacionGenerada',
    ])).ClasificacionGenerada;
    const generada = generadaMensaje.sobre;
    assert.equal(generada.source, 'classification-service');
    assert.equal(generada.payload.ticketId, caso.ticketId);
    assert.equal(generada.payload.resultado, 'CLASIFICADO', JSON.stringify(generada.payload));
    assert.equal(generada.payload.clasificacion.proveedor, 'mock', 'Usar LLM_PROVIDER=mock para esta prueba');
    assert.equal(generada.payload.clasificacion.requiereEscalamiento, true, 'El mock tendria que marcar el ticket como critico');
    const guardadas = await ctx.clasificacionesGuardadas(caso);
    assert.equal(guardadas.length, 1, `Se esperaba una clasificacion guardada y hay ${guardadas.length}`);
    assert.equal(guardadas[0].id, generada.payload.clasificacion.id, 'La clasificacion guardada no es la que se publico');
    console.log(`OK  classification-service guardo la clasificacion ${guardadas[0].id}, la misma que publico`);

    // 4. Escalar
    const ticket = await esperarQue('4. Escalar: el ticket quedo P1 y ESCALADO', async () => {
      const visto = await ctx.gateway(`/api/tickets/${caso.ticketId}`, ctx.tokens.agente, caso.correlationId);
      return { ok: visto.estado === 'ESCALADO', valor: visto, detalle: visto.estado };
    });
    assert.equal(ticket.prioridad, 'P1');
    assert.equal(ticket.clasificacion?.proveedor, 'mock');
    assert.ok(ticket.motivoEscalamiento, 'El ticket escalado no tiene motivoEscalamiento');
    assert.ok(ticket.fechaLimiteSla, 'El ticket clasificado no tiene fechaLimiteSla');
    const escalamiento = await esperarMensajes('ticket-service publico TicketClasificado y TicketEscalado', [
      'TicketClasificado',
      'TicketEscalado',
    ]);
    assert.equal(escalamiento.TicketClasificado.sobre.payload.ticket.prioridad, 'P1');
    const escalado = escalamiento.TicketEscalado.sobre;
    assert.equal(escalado.payload.ticket.ticketId, caso.ticketId);
    assert.equal(escalado.payload.ticket.estado, 'ESCALADO');
    assert.ok(escalado.payload.motivo, 'TicketEscalado no trae motivo');

    // Orden causal y propiedades AMQP de todos los mensajes del camino.
    const posicion = (tipo) => recibidos.findIndex((m) => m.sobre.eventType === tipo);
    for (const [antes, despues] of [
      ['ClasificacionSolicitada', 'ClasificacionGenerada'],
      ['ClasificacionGenerada', 'TicketClasificado'],
      ['TicketClasificado', 'TicketEscalado'],
    ]) {
      assert.ok(posicion(antes) < posicion(despues), `${antes} tendria que llegar antes que ${despues}: ${recibidos.map((m) => m.sobre.eventType)}`);
    }
    for (const tipo of MENSAJES_DEL_CAMINO) {
      const { sobre, propiedades } = recibidos[posicion(tipo)];
      assert.equal(propiedades.message_id, sobre.eventId, `${tipo}: message_id distinto del eventId`);
      assert.equal(propiedades.correlation_id, caso.correlationId, `${tipo}: correlation_id distinto del X-Correlation-Id del alta`);
      assert.equal(propiedades.type, tipo, `${tipo}: la propiedad type no es el eventType`);
    }
    console.log(`OK  orden y correlacion: ${MENSAJES_DEL_CAMINO.join(', ')} con el correlationId del alta`);

    // 5. Notificar y 6. Reportar
    await ctx.esperarNotificacion(caso);
    await ctx.esperarProyeccion(caso);

    // 7. Colas y DLQ
    await ctx.esperarColasYDlqsVacias();
  } finally {
    await ctx.rabbit(`/queues/${VHOST}/${cola}`, { metodo: 'DELETE', status: [204, 404] });
    console.log(`OK  cola temporal ${COLA_ESPIA} borrada`);
  }
}

// ---------- Ejecucion ----------

const CASOS = [
  ['contrato', 'Contrato de los consumidores (publicando a mano)', casoContrato],
  ['punta-a-punta', 'Punta a punta desde el gateway', casoPuntaAPunta],
];

async function main() {
  const pedidos = process.argv.slice(2);
  const desconocidos = pedidos.filter((p) => !CASOS.some(([clave]) => clave === p));
  assert.equal(desconocidos.length, 0, `Casos desconocidos: ${desconocidos.join(', ')} (hay: ${CASOS.map(([c]) => c).join(', ')})`);
  const elegidos = CASOS.filter(([clave]) => pedidos.length === 0 || pedidos.includes(clave));

  const ctx = crearContexto();
  ctx.tokens.solicitante = await ctx.login('solicitante@urgentia.local', 'Usuario123!');
  ctx.tokens.agente = await ctx.login('guardia@urgentia.local', 'Agente123!');
  ctx.tokens.admin = await ctx.login('admin@urgentia.local', 'Admin123!');

  for (const [, titulo, caso] of elegidos) {
    console.log(`\n=== ${titulo}`);
    await caso(ctx);
  }
  console.log('\nMensajeria verificada');
}

main().catch((error) => {
  console.error(`FALLO: ${error.message}`);
  process.exitCode = 1;
});
