// Pruebas de falla de la mensajeria sobre el Compose levantado (LLM_PROVIDER=mock).
//
// Complementa verificar-mensajeria.cjs (camino feliz). Publica a mano por la API de administracion
// de RabbitMQ y frena o reconfigura contenedores con "docker compose". Cada prueba deja todo como
// estaba, aunque falle.
//
//   dlq         1. Mensaje invalido: en cada cola de trabajo, un mensaje que no es JSON y uno sin
//                  payload. Los dos tienen que llegar a <cola>.dlq por rechazo (x-death "rejected"),
//                  sin reintentos.
//   consumidor  2. Consumidor caido: con notification-service y reporting-service frenados, un
//                  TicketEscalado queda en notificaciones.eventos y reportes.eventos; al levantarlos
//                  lo procesan y las colas se vacian.
//   broker      3. Broker caido: se frena rabbitmq y se levanta. Los tres consumidores se reconectan
//                  solos (consumidores en las colas y conexiones en la API de administracion) y
//                  procesan un evento publicado despues de la caida.
//   ia          4. IA caida: classification-service con LLM_PROVIDER=ollama apuntando a un puerto sin
//                  nadie (conexion rechazada) y despues a un "LLM" que acepta la conexion y no
//                  contesta (timeout). En los dos casos publica ClasificacionGenerada con resultado
//                  ERROR, no reintenta, no manda a la DLQ y no guarda clasificacion. Al final vuelve
//                  a la configuracion del .env y se comprueba que clasifica otra vez.
//
// Para leer las ClasificacionGenerada sin tocar tickets.clasificaciones (la cola de ticket-service),
// se crea una cola temporal propia enlazada al mismo routing key, y se borra al final.
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
const COLAS_DE_TRABAJO = [NOTIFICACIONES, REPORTES, SOLICITUDES];
const DLQS = [...COLAS_DE_TRABAJO, 'tickets.clasificaciones'].map((cola) => `${cola}.dlq`);
const COLA_ESPIA = 'ci.fallas.clasificaciones-generadas';
const PUERTO_LLM_MUDO = 9999;
// Acepta conexiones y nunca contesta. Corre dentro del contenedor de classification-service.
const LLM_MUDO = `import socket,itertools; s=socket.create_server(('127.0.0.1', ${PUERTO_LLM_MUDO})); c=[s.accept() for _ in itertools.count()]`;
const LLM_MUDO_ESCUCHA = `import socket; socket.create_connection(('127.0.0.1', ${PUERTO_LLM_MUDO}), 1).close()`;
// Sofia Solicitante, de los usuarios semilla de user-service (DatosSemilla).
const SOLICITANTE_SEMILLA = 'b1c2d3e4-0000-4000-8000-000000000004';

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
    signal: AbortSignal.timeout(10000),
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
    ticketId: randomUUID(),
    correlationId: `p1-fallas-${prueba}-${randomUUID()}`,
    titulo: 'Caida de produccion (prueba de falla)',
    descripcion: 'Produccion caida: no funciona el login y todos los usuarios estan bloqueados.',
  };
}

function sobre(caso, eventType, payload) {
  return {
    eventId: randomUUID(),
    eventType,
    version: 1,
    occurredAt: fecha(new Date()),
    correlationId: caso.correlationId,
    source: 'ticket-service',
    payload,
  };
}

function ticketEscalado(caso) {
  const ahora = new Date();
  return sobre(caso, 'TicketEscalado', {
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
  });
}

function clasificacionSolicitada(caso) {
  return sobre(caso, 'ClasificacionSolicitada', {
    ticketId: caso.ticketId,
    titulo: caso.titulo,
    descripcion: caso.descripcion,
  });
}

// ---------- Contexto compartido por las pruebas ----------

function crearContexto() {
  const authRabbit = credencialesRabbit();
  const rabbit = (ruta, opciones = {}) =>
    pedir(`${RABBIT_API}${ruta}`, { ...opciones, headers: { Authorization: authRabbit } });
  const cola = async (nombre) => rabbit(`/queues/${VHOST}/${encodeURIComponent(nombre)}`);
  const mensajesEn = async (nombre) => (await cola(nombre)).messages ?? 0;
  const tokens = {};

  // Publica en un exchange; con amq.default y routing key = nombre de la cola, va directo a esa cola.
  async function publicar(exchange, routingKey, payload, { messageId, correlationId, type }) {
    const resultado = await rabbit(`/exchanges/${VHOST}/${encodeURIComponent(exchange)}/publish`, {
      metodo: 'POST',
      body: {
        routing_key: routingKey,
        payload,
        payload_encoding: 'string',
        properties: {
          content_type: 'application/json',
          content_encoding: 'utf-8',
          delivery_mode: 2,
          message_id: messageId,
          correlation_id: correlationId,
          ...(type ? { type, app_id: 'ticket-service' } : {}),
        },
      },
    });
    assert.equal(resultado.routed, true, `El mensaje ${messageId} no llego a ninguna cola de ${exchange}`);
  }

  async function publicarSobre(exchange, evento) {
    await publicar(exchange, evento.eventType, JSON.stringify(evento), {
      messageId: evento.eventId,
      correlationId: evento.correlationId,
      type: evento.eventType,
    });
    console.log(`OK  ${evento.eventType} publicado en ${exchange} (ticketId=${evento.payload.ticketId ?? evento.payload.ticket.ticketId})`);
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

  const gateway = (ruta, token, correlationId) =>
    pedir(`${GATEWAY}${ruta}`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Correlation-Id': correlationId || `p1-fallas-${randomUUID()}` },
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

  async function esperarClasificado(caso) {
    const guardada = await esperarQue('classification-service guardo la clasificacion', async () => {
      const contenido = await clasificacionesGuardadas(caso);
      return { ok: contenido.length > 0, valor: contenido[0], detalle: contenido };
    });
    const generada = await esperarGenerada(caso.ticketId);
    assert.equal(generada.correlationId, caso.correlationId, 'ClasificacionGenerada no repite el correlationId');
    assert.equal(generada.payload.resultado, 'CLASIFICADO', JSON.stringify(generada.payload));
    assert.equal(generada.payload.clasificacion.id, guardada.id, 'La clasificacion publicada no es la guardada');
    console.log('OK  ClasificacionGenerada con resultado CLASIFICADO');
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
    rabbit, cola, mensajesEn, publicar, publicarSobre, sacar, esperarGenerada, login, tokens,
    esperarNotificacion, esperarProyeccion, clasificacionesGuardadas, esperarClasificado,
    esperarColasVacias, esperarDlqsVacias, prepararColaEspia, borrarColaEspia,
  };
}

// ---------- 1. Mensaje invalido a la DLQ ----------

async function pruebaMensajeInvalido(ctx) {
  await ctx.esperarDlqsVacias();
  const correlationId = `p1-fallas-dlq-${randomUUID()}`;
  const casos = {
    [NOTIFICACIONES]: 'TicketEscalado',
    [REPORTES]: 'TicketEscalado',
    [SOLICITUDES]: 'ClasificacionSolicitada',
  };
  const esperados = {};
  for (const [cola, eventType] of Object.entries(casos)) {
    const noJson = randomUUID();
    const sinPayload = randomUUID();
    // Directo a la cola (amq.default), para probar cada consumidor por separado.
    await ctx.publicar('amq.default', cola, 'esto no es JSON {', { messageId: noJson, correlationId });
    const { payload, ...sobreSinPayload } = sobre({ correlationId }, eventType, {});
    await ctx.publicar('amq.default', cola, JSON.stringify({ ...sobreSinPayload, eventId: sinPayload }), {
      messageId: sinPayload,
      correlationId,
      type: eventType,
    });
    esperados[cola] = new Set([noJson, sinPayload]);
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
    await ctx.publicarSobre('urgentia.eventos', ticketEscalado(caso));
    await esperarQue(`el TicketEscalado quedo esperando en ${colas.join(' y ')}`, async () => {
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
  compose(['stop', 'rabbitmq']);
  try {
    console.log(`    RabbitMQ frenado; se espera ${BROKER_CAIDO_MS / 1000} s para que los consumidores fallen al reconectarse`);
    await esperar(BROKER_CAIDO_MS);
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
    const servicioDe = { [NOTIFICACIONES]: 'notification-service', [REPORTES]: 'reporting-service', [SOLICITUDES]: 'classification-service' };
    const cuentas = await consumidores().catch(() => ({}));
    const sinConsumidor = COLAS_DE_TRABAJO.filter((c) => !(cuentas[c] >= 1)).map((c) => servicioDe[c]);
    console.error(`    No se reconectaron solos: ${sinConsumidor.join(', ')}. Se reinician para dejar todo como estaba.`);
    if (sinConsumidor.length > 0) compose(['restart', ...sinConsumidor]);
    throw error;
  }
  const conexiones = await esperarQue('hay al menos 3 conexiones en la API de administracion', async () => {
    const lista = await ctx.rabbit('/connections');
    return { ok: lista.length >= 3, valor: lista, detalle: lista.length };
  });
  for (const c of conexiones) console.log(`    conexion de ${c.client_properties?.product ?? '?'} desde ${c.peer_host}`);

  const caso = crearCaso('broker');
  await ctx.publicarSobre('urgentia.eventos', ticketEscalado(caso));
  await ctx.publicarSobre('urgentia.clasificacion', clasificacionSolicitada(caso));
  await ctx.esperarNotificacion(caso);
  await ctx.esperarProyeccion(caso);
  await ctx.esperarClasificado(caso);
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
    assert.equal(generada.correlationId, caso.correlationId, 'ClasificacionGenerada no repite el correlationId');
    assert.equal(generada.payload.resultado, 'ERROR', JSON.stringify(generada.payload));
    assert.equal(generada.payload.clasificacion, undefined, 'Con resultado ERROR no tiene que venir clasificacion');
    assert.equal(generada.payload.error?.codigo, codigo, JSON.stringify(generada.payload));
    assert.ok(mensaje.test(generada.payload.error.mensaje), `Mensaje inesperado: ${generada.payload.error.mensaje}`);
    console.log(`OK  ClasificacionGenerada con resultado ERROR y codigo ${codigo}: "${generada.payload.error.mensaje}"`);
  }

  console.log('    classification-service con LLM_PROVIDER=ollama, LLM_BASE_URL sin nadie escuchando y LLM_TIMEOUT_MS=2000');
  levantar([servicio], conIaCaida);
  try {
    // a) Nadie escucha en el puerto: la conexion se rechaza.
    const rechazada = crearCaso('ia-rechazada');
    casos.push(rechazada);
    await ctx.publicarSobre('urgentia.clasificacion', clasificacionSolicitada(rechazada));
    await verificarError(rechazada, 'LLM_RESPUESTA_INVALIDA', /^No se pudo consultar al LLM: /);

    // b) Un "LLM" que acepta la conexion y no contesta: se vence LLM_TIMEOUT_MS.
    compose(['exec', '-d', '-T', servicio, 'python', '-c', LLM_MUDO]);
    await esperarQue('el LLM mudo escucha dentro del contenedor', async () => {
      execFileSync('docker', ['compose', 'exec', '-T', servicio, 'python', '-c', LLM_MUDO_ESCUCHA], { stdio: 'ignore' });
      return { ok: true };
    });
    const mudo = crearCaso('ia-timeout');
    casos.push(mudo);
    await ctx.publicarSobre('urgentia.clasificacion', clasificacionSolicitada(mudo));
    await verificarError(mudo, 'LLM_TIMEOUT', /^El LLM no respondio en 2000 ms$/);

    // Ninguna se reintenta ni se guarda.
    await ctx.esperarColasVacias([SOLICITUDES]);
    for (const caso of casos) {
      assert.deepEqual(await ctx.clasificacionesGuardadas(caso), [], `Se guardo una clasificacion de ${caso.ticketId}`);
    }
    console.log('OK  no se guardo ninguna clasificacion de las solicitudes con la IA caida');
    await ctx.esperarDlqsVacias();
  } finally {
    console.log('    classification-service vuelve a la configuracion del .env');
    levantar([servicio]);
  }

  const despues = crearCaso('ia-restaurada');
  await ctx.publicarSobre('urgentia.clasificacion', clasificacionSolicitada(despues));
  await ctx.esperarClasificado(despues);
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
