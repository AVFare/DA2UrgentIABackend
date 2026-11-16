// Prueba de integracion de la mensajeria sobre el Compose levantado (LLM_PROVIDER=mock).
//
// Origen: publica a mano, por la API de administracion de RabbitMQ, un TicketEscalado y un
// ClasificacionSolicitada validos segun contracts/events/ y contracts/messaging/.
// Cuando ticket-service publique por RabbitMQ (P2), el origen pasa a ser crear el ticket por el gateway.
//
// Verificaciones (todas por ticketId):
//   1. notification-service registra la notificacion a GRUPO:GUARDIA (GET /api/notificaciones).
//   2. reporting-service proyecta el ticket como ESCALADO (GET /api/reportes/tickets).
//   3. classification-service guarda la clasificacion (GET /api/clasificaciones) y publica
//      ClasificacionGenerada, que consume ticket-service (como el ticket es de prueba, la confirma sin efectos).
//   4. Las colas de trabajo se vacian y las DLQ quedan vacias.
//
// Uso, desde la raiz del repo y con el Compose levantado:  node infra/ci/verificar-mensajeria.cjs
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

async function pedir(url, { metodo = 'GET', headers = {}, body, status = 200 } = {}) {
  const respuesta = await fetch(url, {
    method: metodo,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(10000),
  });
  const texto = await respuesta.text();
  if (respuesta.status !== status) {
    throw new Error(`${metodo} ${url}: HTTP ${respuesta.status} (se esperaba ${status}) ${texto.slice(0, 300)}`);
  }
  return texto ? JSON.parse(texto) : null;
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

function crearCaso() {
  return {
    ticketId: randomUUID(),
    correlationId: `p1-mensajeria-${randomUUID()}`,
    titulo: 'Caida de produccion (prueba de mensajeria)',
    descripcion: 'Produccion caida: no funciona el login y todos los usuarios estan bloqueados.',
  };
}

async function main() {
  const caso = crearCaso();
  console.log(`Caso: ticketId=${caso.ticketId} correlationId=${caso.correlationId}`);
  const authRabbit = credencialesRabbit();
  const rabbit = (ruta, opciones = {}) =>
    pedir(`${RABBIT_API}${ruta}`, { ...opciones, headers: { Authorization: authRabbit } });
  const mensajesEn = async (cola) => (await rabbit(`/queues/${VHOST}/${encodeURIComponent(cola)}`)).messages ?? 0;

  async function login(email, password) {
    const sesion = await esperarQue(`login de ${email}`, async () => ({
      ok: true,
      valor: await pedir(`${GATEWAY}/api/auth/login`, { metodo: 'POST', body: { email, password } }),
    }));
    return sesion.accessToken;
  }

  // ---------- Origen: publicar a mano (se reemplaza cuando ticket-service publique por RabbitMQ) ----------

  async function publicar(exchange, sobre) {
    const resultado = await rabbit(`/exchanges/${VHOST}/${encodeURIComponent(exchange)}/publish`, {
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

  async function publicarAMano(caso) {
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
  }

  // ---------- Verificaciones ----------

  const tokenAgente = await login('guardia@urgentia.local', 'Agente123!');
  const tokenAdmin = await login('admin@urgentia.local', 'Admin123!');
  const gateway = (ruta, token) =>
    pedir(`${GATEWAY}${ruta}`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Correlation-Id': caso.correlationId },
    });

  await publicarAMano(caso);

  await esperarQue('notification-service registro la notificacion a GRUPO:GUARDIA', async () => {
    const pagina = await gateway(`/api/notificaciones?ticketId=${caso.ticketId}&size=100`, tokenAgente);
    const ok = pagina.content.some(
      (n) => n.ticketId === caso.ticketId && n.tipoEvento === 'TicketEscalado' && n.destinatario === 'GRUPO:GUARDIA',
    );
    return { ok, detalle: pagina.content };
  });

  await esperarQue('reporting-service proyecto el ticket como ESCALADO', async () => {
    const pagina = await gateway('/api/reportes/tickets?prioridad=P1&estado=ESCALADO&size=100', tokenAgente);
    const vista = pagina.content.find((v) => v.ticketId === caso.ticketId);
    return { ok: vista?.estado === 'ESCALADO' && vista?.escalado === true, detalle: vista ?? 'sin vista' };
  });

  await esperarQue('classification-service guardo la clasificacion', async () => {
    const pagina = await gateway(`/api/clasificaciones?ticketId=${caso.ticketId}`, tokenAdmin);
    return { ok: pagina.content.length > 0, valor: pagina.content[0], detalle: pagina };
  });

  await esperarQue(`las colas de trabajo quedaron vacias (${COLAS_DE_TRABAJO.join(', ')})`, async () => {
    const cuentas = Object.fromEntries(await Promise.all(COLAS_DE_TRABAJO.map(async (c) => [c, await mensajesEn(c)])));
    return { ok: Object.values(cuentas).every((n) => n === 0), detalle: cuentas };
  });

  const enDlq = Object.fromEntries(await Promise.all(DLQS.map(async (c) => [c, await mensajesEn(c)])));
  assert.ok(Object.values(enDlq).every((n) => n === 0), `Hay mensajes en las DLQ: ${JSON.stringify(enDlq)}`);
  console.log('OK  las DLQ estan vacias');

  console.log('Mensajeria verificada');
}

main().catch((error) => {
  console.error(`FALLO: ${error.message}`);
  process.exitCode = 1;
});
