// Pruebas de falla de la mensajeria sobre el Compose levantado (LLM_PROVIDER=mock, CLASIFICACION_MODO=async).
//
// Complementa verificar-mensajeria.cjs (camino feliz). Crea tickets por el gateway (ticket-service los
// publica por el Outbox), publica a mano por la API de administracion de RabbitMQ cuando hace falta un
// mensaje invalido, y frena o reconfigura contenedores con "docker compose". Cada prueba deja todo como
// estaba, aunque falle.
//
//   dlq         1. Mensaje invalido: en cada una de las 4 colas de trabajo, un mensaje que no es JSON y
//                  uno sin payload. Los dos tienen que llegar a <cola>.dlq por rechazo (x-death
//                  "rejected"), sin reintentos.
//   consumidor  2. Consumidor caido: con notification-service y reporting-service frenados se crea un
//                  ticket critico. Se clasifica y escala igual, y sus eventos quedan esperando en
//                  notificaciones.eventos y reportes.eventos; al levantarlos los procesan y las colas
//                  se vacian.
//   broker      3. Broker caido: con rabbitmq frenado se crea un ticket (el alta no depende del broker:
//                  los mensajes quedan en el Outbox). Al levantarlo, los cuatro consumidores se
//                  reconectan solos (consumidores en las colas y conexiones en la API de administracion),
//                  el Outbox publica lo pendiente y el ticket termina clasificado, escalado, notificado
//                  y proyectado.
//   ia          4. IA caida: classification-service con LLM_PROVIDER=ollama apuntando a un puerto sin
//                  nadie (conexion rechazada) y despues a un "LLM" que acepta la conexion y no contesta
//                  (timeout). En los dos casos el worker publica ClasificacionGenerada con resultado
//                  ERROR, no reintenta, no manda a la DLQ y no guarda clasificacion; el ticket queda en
//                  PENDIENTE_CLASIFICACION y reclasificarlo con la IA caida da 503 IA_NO_DISPONIBLE. Al
//                  volver a la configuracion del .env, los dos se reclasifican y un ticket nuevo se
//                  clasifica por la cola.
//
// Para leer las ClasificacionGenerada sin quitarselas a ticket-service, se crea una cola temporal
// propia enlazada al mismo routing key, y se borra al final.
//
// Uso, desde la raiz del repo y con el Compose levantado:
//   node infra/ci/verificar-fallas-mensajeria.cjs                 (las cuatro, en orden)
//   node infra/ci/verificar-fallas-mensajeria.cjs dlq consumidor  (solo esas)
// Precondicion: colas de trabajo y DLQ vacias. Toma RABBITMQ_USER y RABBITMQ_PASSWORD del entorno o del .env.
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const { randomUUID } = require('node:crypto');
const { setTimeout: esperar } = require('node:timers/promises');

const GATEWAY = process.env.GATEWAY_URL || 'http://localhost:8080';
const RABBIT_API = process.env.RABBITMQ_API_URL || 'http://localhost:15672/api';
const VHOST = '%2F';
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS || 90000);
// Las DLQ se miran por el contador de la API, que se actualiza cada 5 s.
const PLAZO_DLQ_VACIAS_MS = 15000;
const INTERVALO_MS = 1000;
// Mas que los 5 s que esperan los consumidores entre intentos de reconexion.
const BROKER_CAIDO_MS = 12000;

const NOTIFICACIONES = 'notificaciones.eventos';
const REPORTES = 'reportes.eventos';
const SOLICITUDES = 'clasificacion.solicitudes';
const CLASIFICACIONES = 'tickets.clasificaciones';
const COLAS_DE_TRABAJO = [NOTIFICACIONES, REPORTES, SOLICITUDES, CLASIFICACIONES];
const DLQS = COLAS_DE_TRABAJO.map((cola) => `${cola}.dlq`);
const SERVICIO_DE = {
  [NOTIFICACIONES]: 'notification-service',
  [REPORTES]: 'reporting-service',
  [SOLICITUDES]: 'classification-service',
  [CLASIFICACIONES]: 'ticket-service',
};
const COLA_ESPIA = 'ci.fallas.clasificaciones-generadas';
const PUERTO_LLM_MUDO = 9999;
// Acepta conexiones y nunca contesta. Corre dentro del contenedor de classification-service.
const LLM_MUDO = `import socket,itertools; s=socket.create_server(('127.0.0.1', ${PUERTO_LLM_MUDO})); c=[s.accept() for _ in itertools.count()]`;
const LLM_MUDO_ESCUCHA = `import socket; socket.create_connection(('127.0.0.1', ${PUERTO_LLM_MUDO}), 1).close()`;

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

async function pedir(url, { metodo = 'GET', headers = {}, body, status = [200] } = {}) {
  const respuesta = await fetch(url, {
    method: metodo,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(15000),
  });
  const texto = await respuesta.text();
  if (!status.includes(respuesta.status)) {
    throw new Error(`${metodo} ${url}: HTTP ${respuesta.status} (se esperaba ${status.join(' o ')}) ${texto.slice(0, 300)}`);
  }
  return texto ? JSON.parse(texto) : null;
}

// Reintenta condicion() hasta que devuelva { ok: true, valor } o se venza el plazo.
async function esperarQue(descripcion, condicion, plazoMs = TIMEOUT_MS) {
  const limite = Date.now() + plazoMs;
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
  throw new Error(`No se cumplio en ${plazoMs / 1000} s: ${descripcion}. Ultimo estado: ${JSON.stringify(ultimo.detalle)}`);
}

// docker compose con la salida a la vista. env: el entorno del proceso, con lo que se quiera pisar.
function compose(args, env = process.env) {
  console.log(`    docker compose ${args.join(' ')}`);
  execFileSync('docker', ['compose', ...args], { stdio: 'inherit', env });
}

function levantar(servicios, env) {
  compose(['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', ...servicios], env);
}

const fecha = (d) => d.toISOString().replace(/\.\d{3}Z$/, 'Z');

function crearCaso(prueba) {
  return {
    ticketId: null,
    correlationId: `p1-fallas-${prueba}-${randomUUID()}`,
    titulo: `Caida de produccion (prueba de falla: ${prueba})`,
    descripcion: 'Produccion caida: no funciona el login y todos los usuarios estan bloqueados.',
  };
}

function sobre(eventType, source, correlationId) {
  return {
    eventId: randomUUID(),
    eventType,
    version: 1,
    occurredAt: fecha(new Date()),
    correlationId,
    source,
  };
}

// ---------- Contexto compartido por las pruebas ----------

function crearContexto() {
  const authRabbit = credencialesRabbit();
  const rabbit = (ruta, opciones = {}) =>
    pedir(`${RABBIT_API}${ruta}`, { ...opciones, headers: { Authorization: authRabbit } });
  const cola = async (nombre) => rabbit(`/queues/${VHOST}/${encodeURIComponent(nombre)}`);
  const mensajesEn = async (nombre) => (await cola(nombre)).messages ?? 0;
  const tokens = {};

  // Con amq.default y routing key = nombre de la cola, el mensaje va directo a esa cola.
  async function publicarEnCola(nombre, payload, { messageId, correlationId, type, appId }) {
    const resultado = await rabbit(`/exchanges/${VHOST}/amq.default/publish`, {
      metodo: 'POST',
      body: {
        routing_key: nombre,
        payload,
        payload_encoding: 'string',
        properties: {
          content_type: 'application/json',
          content_encoding: 'utf-8',
          delivery_mode: 2,
          message_id: messageId,
          correlation_id: correlationId,
          ...(type ? { type, app_id: appId } : {}),
        },
      },
    });
    assert.equal(resultado.routed, true, `El mensaje ${messageId} no llego a ${nombre}`);
  }

  // Saca mensajes de una cola (ack_requeue_false). Solo para colas propias o que tienen que estar vacias.
  async function sacar(nombre, cantidad = 50) {
    return rabbit(`/queues/${VHOST}/${encodeURIComponent(nombre)}/get`, {
      metodo: 'POST',
      body: { count: cantidad, ackmode: 'ack_requeue_false', encoding: 'auto' },
    });
  }

  const generadas = new Map();
  async function esperarGenerada(ticketId) {
    return esperarQue(`ClasificacionGenerada del ticket ${ticketId}`, async () => {
      for (const mensaje of await sacar(COLA_ESPIA)) {
        const evento = JSON.parse(mensaje.payload);
        generadas.set(evento.payload?.ticketId, evento);
      }
      return { ok: generadas.has(ticketId), valor: generadas.get(ticketId), detalle: [...generadas.keys()] };
    });
  }

  async function login(email, password) {
    const sesion = await esperarQue(`login de ${email}`, async () => ({
      ok: true,
      valor: await pedir(`${GATEWAY}/api/auth/login`, { metodo: 'POST', body: { email, password } }),
    }));
    return sesion.accessToken;
  }

  const gateway = (ruta, token, correlationId, { metodo = 'GET', body, status = [200] } = {}) =>
    pedir(`${GATEWAY}${ruta}`, {
      metodo,
      body,
      status,
      headers: { Authorization: `Bearer ${token}`, 'X-Correlation-Id': correlationId || `p1-fallas-${randomUUID()}` },
    });

  // ---------- Tickets por el gateway ----------

  async function crearTicket(caso) {
    const ticket = await gateway('/api/tickets', tokens.solicitante, caso.correlationId, {
      metodo: 'POST',
      body: { titulo: caso.titulo, descripcion: caso.descripcion },
      status: [201],
    });
    assert.ok(ticket.id, 'El alta no devolvio el id del ticket');
    caso.ticketId = ticket.id;
    console.log(`OK  ticket ${ticket.id} creado por el gateway en ${ticket.estado}`);
    return ticket;
  }

  const verTicket = (caso) => gateway(`/api/tickets/${caso.ticketId}`, tokens.agente, caso.correlationId);

  async function esperarEstado(caso, estado) {
    return esperarQue(`el ticket ${caso.ticketId} quedo en ${estado}`, async () => {
      const ticket = await verTicket(caso);
      return { ok: ticket.estado === estado, valor: ticket, detalle: ticket.estado };
    });
  }

  async function reclasificar(caso, status) {
    return gateway(`/api/tickets/${caso.ticketId}/reclasificacion`, tokens.agente, caso.correlationId, {
      metodo: 'POST',
      status: [status],
    });
  }

  // ---------- Efectos en los consumidores ----------

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

  async function esperarColasVacias(colas) {
    await esperarQue(`quedaron vacias ${colas.join(', ')}`, async () => {
      const cuentas = Object.fromEntries(await Promise.all(colas.map(async (c) => [c, await mensajesEn(c)])));
      return { ok: Object.values(cuentas).every((n) => n === 0), detalle: cuentas };
    });
  }

  async function esperarDlqsVacias() {
    await esperarQue(
      'las DLQ estan vacias',
      async () => {
        const cuentas = Object.fromEntries(await Promise.all(DLQS.map(async (c) => [c, await mensajesEn(c)])));
        return { ok: Object.values(cuentas).every((n) => n === 0), detalle: cuentas };
      },
      PLAZO_DLQ_VACIAS_MS,
    );
  }

  async function prepararColaEspia() {
    await rabbit(`/queues/${VHOST}/${encodeURIComponent(COLA_ESPIA)}`, {
      metodo: 'PUT',
      body: { durable: true, auto_delete: false, arguments: { 'x-queue-type': 'classic' } },
      status: [201, 204],
    });
    await rabbit(`/bindings/${VHOST}/e/urgentia.clasificacion/q/${encodeURIComponent(COLA_ESPIA)}`, {
      metodo: 'POST',
      body: { routing_key: 'ClasificacionGenerada' },
      status: [201],
    });
    console.log(`OK  cola temporal ${COLA_ESPIA} enlazada a ClasificacionGenerada`);
  }

  async function borrarColaEspia() {
    await rabbit(`/queues/${VHOST}/${encodeURIComponent(COLA_ESPIA)}`, { metodo: 'DELETE', status: [204, 404] });
    console.log(`OK  cola temporal ${COLA_ESPIA} borrada`);
  }

  return {
    rabbit, cola, mensajesEn, publicarEnCola, sacar, esperarGenerada, login, tokens,
    crearTicket, verTicket, esperarEstado, reclasificar,
    esperarNotificacion, esperarProyeccion, clasificacionesGuardadas,
    esperarColasVacias, esperarDlqsVacias, prepararColaEspia, borrarColaEspia,
  };
}

// ---------- 1. Mensaje invalido a la DLQ ----------

async function pruebaMensajeInvalido(ctx) {
  await ctx.esperarDlqsVacias();
  const correlationId = `p1-fallas-dlq-${randomUUID()}`;
  // Cada cola con el tipo de mensaje y el source que espera su consumidor, para que lo unico
  // invalido sea lo que la prueba rompe.
  const casos = {
    [NOTIFICACIONES]: ['TicketEscalado', 'ticket-service'],
    [REPORTES]: ['TicketEscalado', 'ticket-service'],
    [SOLICITUDES]: ['ClasificacionSolicitada', 'ticket-service'],
    [CLASIFICACIONES]: ['ClasificacionGenerada', 'classification-service'],
  };
  const esperados = {};
  for (const [cola, [eventType, source]] of Object.entries(casos)) {
    const noJson = randomUUID();
    const sinPayload = { ...sobre(eventType, source, correlationId) };
    await ctx.publicarEnCola(cola, 'esto no es JSON {', { messageId: noJson, correlationId });
    await ctx.publicarEnCola(cola, JSON.stringify(sinPayload), {
      messageId: sinPayload.eventId,
      correlationId,
      type: eventType,
      appId: source,
    });
    esperados[cola] = new Set([noJson, sinPayload.eventId]);
    console.log(`OK  publicados en ${cola}: uno que no es JSON y un ${eventType} sin payload`);
  }

  for (const [cola, ids] of Object.entries(esperados)) {
    const dlq = `${cola}.dlq`;
    const muertos = [];
    await esperarQue(`los dos mensajes invalidos llegaron a ${dlq}`, async () => {
      muertos.push(...(await ctx.sacar(dlq)));
      return { ok: muertos.length >= ids.size, detalle: muertos.map((m) => m.properties.message_id) };
    });
    for (const mensaje of muertos) {
      const id = mensaje.properties.message_id;
      const headers = mensaje.properties.headers ?? {};
      const motivo = headers['x-first-death-reason'] ?? headers['x-death']?.[0]?.reason;
      const origen = headers['x-first-death-queue'] ?? headers['x-death']?.[0]?.queue;
      assert.ok(ids.has(id), `${dlq} tiene un mensaje que no es de la prueba: ${id}`);
      assert.equal(motivo, 'rejected', `${id} llego a ${dlq} por "${motivo}" y no por rechazo directo`);
      assert.equal(origen, cola, `${id} viene de ${origen} y no de ${cola}`);
    }
    console.log(`OK  ${dlq}: los dos rechazados sin reintentos (x-death "rejected")`);
  }
  await ctx.esperarColasVacias(COLAS_DE_TRABAJO);
  await ctx.esperarDlqsVacias();
}

// ---------- 2. Consumidor caido ----------

async function pruebaConsumidorCaido(ctx) {
  const servicios = ['notification-service', 'reporting-service'];
  const colas = [NOTIFICACIONES, REPORTES];
  await ctx.esperarColasVacias(colas);
  const caso = crearCaso('consumidor');

  compose(['stop', ...servicios]);
  try {
    await esperarQue(`${colas.join(' y ')} sin consumidores`, async () => {
      const consumidores = Object.fromEntries(await Promise.all(colas.map(async (c) => [c, (await ctx.cola(c)).consumers])));
      return { ok: Object.values(consumidores).every((n) => n === 0), detalle: consumidores };
    });
    await ctx.crearTicket(caso);
    // La clasificacion por la cola no depende de los servicios frenados.
    await ctx.esperarEstado(caso, 'ESCALADO');
    await esperarQue(`los eventos del ticket quedaron esperando en ${colas.join(' y ')}`, async () => {
      const cuentas = Object.fromEntries(await Promise.all(colas.map(async (c) => [c, await ctx.mensajesEn(c)])));
      return { ok: Object.values(cuentas).every((n) => n >= 1), detalle: cuentas };
    });
  } finally {
    levantar(servicios);
  }

  await ctx.esperarNotificacion(caso);
  await ctx.esperarProyeccion(caso);
  await ctx.esperarColasVacias(colas);
  await ctx.esperarDlqsVacias();
}

// ---------- 3. Broker caido ----------

async function pruebaBrokerCaido(ctx) {
  const caso = crearCaso('broker');
  compose(['stop', 'rabbitmq']);
  try {
    // El alta solo guarda el ticket y sus mensajes en el Outbox, en la misma transaccion.
    const creado = await ctx.crearTicket(caso);
    assert.equal(creado.estado, 'PENDIENTE_CLASIFICACION', 'Con el broker caido el alta tiene que responder PENDIENTE_CLASIFICACION');
    console.log(`    RabbitMQ frenado; se espera ${BROKER_CAIDO_MS / 1000} s para que los consumidores fallen al reconectarse`);
    await esperar(BROKER_CAIDO_MS);
    assert.equal((await ctx.verTicket(caso)).estado, 'PENDIENTE_CLASIFICACION', 'El ticket cambio de estado con el broker caido');
  } finally {
    levantar(['rabbitmq']);
  }

  const consumidores = async () =>
    Object.fromEntries(await Promise.all(COLAS_DE_TRABAJO.map(async (c) => [c, (await ctx.cola(c)).consumers ?? 0])));
  try {
    await esperarQue('cada cola de trabajo tiene consumidor otra vez', async () => {
      const cuentas = await consumidores();
      return { ok: Object.values(cuentas).every((n) => n >= 1), detalle: cuentas };
    });
  } catch (error) {
    // La prueba ya fallo; se reinicia el servicio que no se reconecto para dejar todo como estaba.
    const cuentas = await consumidores().catch(() => ({}));
    const sinConsumidor = COLAS_DE_TRABAJO.filter((c) => !(cuentas[c] >= 1)).map((c) => SERVICIO_DE[c]);
    console.error(`    No se reconectaron solos: ${sinConsumidor.join(', ')}. Se reinician para dejar todo como estaba.`);
    if (sinConsumidor.length > 0) compose(['restart', ...sinConsumidor]);
    throw error;
  }
  const conexiones = await esperarQue('hay al menos 4 conexiones en la API de administracion', async () => {
    const lista = await ctx.rabbit('/connections');
    return { ok: lista.length >= 4, valor: lista, detalle: lista.length };
  });
  for (const c of conexiones) console.log(`    conexion de ${c.client_properties?.product ?? '?'} desde ${c.peer_host}`);

  // El Outbox publica lo pendiente: ClasificacionSolicitada -> clasificacion -> escalado -> eventos.
  await ctx.esperarEstado(caso, 'ESCALADO');
  await ctx.esperarNotificacion(caso);
  await ctx.esperarProyeccion(caso);
  await ctx.esperarColasVacias(COLAS_DE_TRABAJO);
  await ctx.esperarDlqsVacias();
}

// ---------- 4. IA caida ----------

async function pruebaIaCaida(ctx) {
  const servicio = 'classification-service';
  const conIaCaida = {
    ...process.env,
    LLM_PROVIDER: 'ollama',
    LLM_BASE_URL: `http://127.0.0.1:${PUERTO_LLM_MUDO}/v1`,
    LLM_TIMEOUT_MS: '2000',
  };
  const casos = [];

  async function verificarError(caso, codigo, mensaje) {
    const generada = await ctx.esperarGenerada(caso.ticketId);
    assert.equal(generada.correlationId, caso.correlationId, 'ClasificacionGenerada no repite el correlationId del alta');
    assert.equal(generada.payload.resultado, 'ERROR', JSON.stringify(generada.payload));
    assert.equal(generada.payload.clasificacion, undefined, 'Con resultado ERROR no tiene que venir clasificacion');
    assert.equal(generada.payload.error?.codigo, codigo, JSON.stringify(generada.payload));
    assert.ok(mensaje.test(generada.payload.error.mensaje), `Mensaje inesperado: ${generada.payload.error.mensaje}`);
    console.log(`OK  ClasificacionGenerada con resultado ERROR y codigo ${codigo}: "${generada.payload.error.mensaje}"`);
    // ticket-service la consume y el ticket queda para reclasificar.
    await ctx.esperarColasVacias([SOLICITUDES, CLASIFICACIONES]);
    assert.equal((await ctx.verTicket(caso)).estado, 'PENDIENTE_CLASIFICACION', 'El ticket no quedo en PENDIENTE_CLASIFICACION');
    console.log('OK  el ticket sigue en PENDIENTE_CLASIFICACION');
  }

  console.log('    classification-service con LLM_PROVIDER=ollama, LLM_BASE_URL sin nadie escuchando y LLM_TIMEOUT_MS=2000');
  levantar([servicio], conIaCaida);
  try {
    // a) Nadie escucha en el puerto: la conexion se rechaza.
    const rechazada = crearCaso('ia-rechazada');
    casos.push(rechazada);
    await ctx.crearTicket(rechazada);
    await verificarError(rechazada, 'LLM_RESPUESTA_INVALIDA', /^No se pudo consultar al LLM: /);
    const respuesta = await ctx.reclasificar(rechazada, 503);
    assert.equal(respuesta.codigo, 'IA_NO_DISPONIBLE', JSON.stringify(respuesta));
    assert.equal((await ctx.verTicket(rechazada)).estado, 'PENDIENTE_CLASIFICACION', 'El 503 cambio el ticket');
    console.log('OK  reclasificar con la IA caida responde 503 IA_NO_DISPONIBLE y el ticket no cambia');

    // b) Un "LLM" que acepta la conexion y no contesta: se vence LLM_TIMEOUT_MS.
    compose(['exec', '-d', '-T', servicio, 'python', '-c', LLM_MUDO]);
    await esperarQue('el LLM mudo escucha dentro del contenedor', async () => {
      execFileSync('docker', ['compose', 'exec', '-T', servicio, 'python', '-c', LLM_MUDO_ESCUCHA], { stdio: 'ignore' });
      return { ok: true };
    });
    const mudo = crearCaso('ia-timeout');
    casos.push(mudo);
    await ctx.crearTicket(mudo);
    await verificarError(mudo, 'LLM_TIMEOUT', /^El LLM no respondio en 2000 ms$/);

    for (const caso of casos) {
      assert.deepEqual(await ctx.clasificacionesGuardadas(caso), [], `Se guardo una clasificacion de ${caso.ticketId}`);
    }
    console.log('OK  no se guardo ninguna clasificacion con la IA caida');
    await ctx.esperarDlqsVacias();
  } finally {
    console.log('    classification-service vuelve a la configuracion del .env');
    levantar([servicio]);
  }

  for (const caso of casos) {
    const ticket = await ctx.reclasificar(caso, 200);
    assert.equal(ticket.estado, 'ESCALADO', `El ticket ${caso.ticketId} se reclasifico en ${ticket.estado}`);
    console.log(`OK  con la IA de vuelta, el ticket ${caso.ticketId} se reclasifico y quedo ESCALADO`);
  }
  const despues = crearCaso('ia-restaurada');
  await ctx.crearTicket(despues);
  await ctx.esperarEstado(despues, 'ESCALADO');
  await ctx.esperarColasVacias(COLAS_DE_TRABAJO);
  await ctx.esperarDlqsVacias();
}

// ---------- Ejecucion ----------

const PRUEBAS = [
  ['dlq', '1. Mensaje invalido a la DLQ', pruebaMensajeInvalido],
  ['consumidor', '2. Consumidor caido', pruebaConsumidorCaido],
  ['broker', '3. Broker caido', pruebaBrokerCaido],
  ['ia', '4. IA caida', pruebaIaCaida],
];

async function main() {
  const pedidas = process.argv.slice(2);
  const desconocidas = pedidas.filter((p) => !PRUEBAS.some(([clave]) => clave === p));
  assert.equal(desconocidas.length, 0, `Pruebas desconocidas: ${desconocidas.join(', ')} (hay: ${PRUEBAS.map(([c]) => c).join(', ')})`);
  const elegidas = PRUEBAS.filter(([clave]) => pedidas.length === 0 || pedidas.includes(clave));

  const ctx = crearContexto();
  ctx.tokens.solicitante = await ctx.login('solicitante@urgentia.local', 'Usuario123!');
  ctx.tokens.agente = await ctx.login('guardia@urgentia.local', 'Agente123!');
  ctx.tokens.admin = await ctx.login('admin@urgentia.local', 'Admin123!');

  const resultados = [];
  try {
    await ctx.prepararColaEspia();
    for (const [, titulo, prueba] of elegidas) {
      console.log(`\n=== ${titulo}`);
      const inicio = Date.now();
      try {
        await prueba(ctx);
        resultados.push({ titulo, ok: true, segundos: Math.round((Date.now() - inicio) / 1000) });
      } catch (error) {
        console.error(`FALLO ${titulo}: ${error.message}`);
        resultados.push({ titulo, ok: false, segundos: Math.round((Date.now() - inicio) / 1000) });
      }
    }
  } finally {
    await ctx.borrarColaEspia();
  }

  console.log('\nResumen');
  for (const r of resultados) console.log(`${r.ok ? 'OK   ' : 'FALLO'} ${r.titulo} (${r.segundos} s)`);
  if (resultados.some((r) => !r.ok)) process.exitCode = 1;
  else console.log('Pruebas de falla verificadas');
}

main().catch((error) => {
  console.error(`FALLO: ${error.message}`);
  process.exitCode = 1;
});
