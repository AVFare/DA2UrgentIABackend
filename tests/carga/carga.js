// Prueba de carga basica de UrgentIA con k6. Como correrla: ver README.md de esta carpeta.
//
// MODO=gateway  crea tickets por el api-gateway (POST /api/tickets), como un solicitante.
// MODO=cola     publica ClasificacionSolicitada en RabbitMQ y mide como la procesa classification-service.
//
// En los dos modos se muestrea una vez por segundo el tamaño de las colas de clasificacion.

import http from 'k6/http';
import encoding from 'k6/encoding';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const MODO = __ENV.MODO || 'gateway';
const TASA = Number(__ENV.TASA || 20);
const DURACION_S = Number(__ENV.DURACION_S || 60);
const GATEWAY = __ENV.GATEWAY_URL || 'http://api-gateway:8080';
const RABBITMQ = __ENV.RABBITMQ_API_URL || 'http://rabbitmq:15672/api';
const USUARIO_RABBITMQ = __ENV.RABBITMQ_USER || 'urgentia';
const CLAVE_RABBITMQ = __ENV.RABBITMQ_PASSWORD || 'rabbit_pass';

const EXCHANGE = 'urgentia.clasificacion';
const COLA_SOLICITUDES = 'clasificacion.solicitudes';
const COLA_MUERTOS = 'clasificacion.solicitudes.dlq';
const COLA_RESPUESTAS = 'carga.respuestas';
const ESPERA_MAXIMA_VACIADO_S = 120;

const TICKETS = [
  ['No puede ingresar nadie', 'Producción caída, todos los usuarios bloqueados en el login desde las 9'],
  ['No puedo pagar con tarjeta', 'Al confirmar el pago con mi tarjeta me da error 500'],
  ['Consulta sobre reportes', '¿Cómo exporto el reporte mensual de ventas a Excel?'],
  ['Alta de usuario nuevo', 'Necesito que den de alta a un empleado nuevo en el sistema con rol de ventas'],
  ['Botón de reporte no anda', 'El botón Descargar PDF del reporte de stock no hace nada en Firefox'],
];

const solicitudesEnCola = new Trend('cola_solicitudes_mensajes');
const mensajesMuertos = new Trend('cola_muertos_mensajes');
const ticketsClasificados = new Counter('tickets_clasificados');
const ticketsPendientes = new Counter('tickets_pendientes_de_clasificacion');
const segundosDeVaciado = new Trend('cola_segundos_hasta_vaciarse');

export const options = {
  scenarios: {
    carga: {
      executor: 'ramping-arrival-rate',
      exec: MODO === 'cola' ? 'publicarSolicitud' : 'crearTicket',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 300,
      stages: [
        { target: TASA, duration: '15s' },
        { target: TASA, duration: `${DURACION_S}s` },
        { target: 0, duration: '5s' },
      ],
    },
    muestreo: {
      executor: 'constant-arrival-rate',
      exec: 'muestrearColas',
      rate: 1,
      timeUnit: '1s',
      duration: `${DURACION_S + 20}s`,
      preAllocatedVUs: 2,
    },
  },
  thresholds: {
    // RNF02: crear un ticket con clasificacion responde en menos de 8 s.
    'http_req_duration{operacion:crear_ticket}': ['p(95)<8000'],
    'http_req_failed{operacion:crear_ticket}': ['rate<0.01'],
    'http_req_failed{operacion:publicar_solicitud}': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'max'],
};

const cabecerasRabbitMq = {
  Authorization: `Basic ${encoding.b64encode(`${USUARIO_RABBITMQ}:${CLAVE_RABBITMQ}`)}`,
  'Content-Type': 'application/json',
};

function rabbitmq(metodo, ruta, cuerpo, etiquetas) {
  return http.request(metodo, `${RABBITMQ}${ruta}`, cuerpo ? JSON.stringify(cuerpo) : null, {
    headers: cabecerasRabbitMq,
    tags: etiquetas || { operacion: 'administracion' },
  });
}

function mensajesEn(cola) {
  const respuesta = rabbitmq('GET', `/queues/%2F/${cola}`);
  return respuesta.status === 200 ? respuesta.json('messages') || 0 : 0;
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function setup() {
  const datos = { muertosAlEmpezar: mensajesEn(COLA_MUERTOS) };
  if (MODO === 'cola') {
    // Alguien tiene que recibir las respuestas: sin una cola enlazada, las solicitudes no se confirman.
    rabbitmq('PUT', `/queues/%2F/${COLA_RESPUESTAS}`, { durable: true, arguments: { 'x-queue-type': 'quorum' } });
    rabbitmq('POST', `/bindings/%2F/e/${EXCHANGE}/q/${COLA_RESPUESTAS}`, { routing_key: 'ClasificacionGenerada' });
    return datos;
  }
  const login = http.post(
    `${GATEWAY}/api/auth/login`,
    JSON.stringify({ email: 'solicitante@urgentia.local', password: 'Usuario123!' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { operacion: 'login' } },
  );
  if (login.status !== 200) {
    throw new Error(`No se pudo iniciar sesion: HTTP ${login.status}`);
  }
  datos.token = login.json('accessToken');
  return datos;
}

export function crearTicket(datos) {
  const [titulo, descripcion] = TICKETS[Math.floor(Math.random() * TICKETS.length)];
  const respuesta = http.post(`${GATEWAY}/api/tickets`, JSON.stringify({ titulo, descripcion }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${datos.token}` },
    tags: { operacion: 'crear_ticket' },
  });
  check(respuesta, { 'el ticket se crea (201)': (r) => r.status === 201 });
  if (respuesta.status === 201) {
    if (respuesta.json('estado') === 'PENDIENTE_CLASIFICACION') {
      ticketsPendientes.add(1);
    } else {
      ticketsClasificados.add(1);
    }
  }
}

export function publicarSolicitud() {
  const [titulo, descripcion] = TICKETS[Math.floor(Math.random() * TICKETS.length)];
  const solicitud = {
    eventId: uuid(),
    eventType: 'ClasificacionSolicitada',
    version: 1,
    occurredAt: new Date().toISOString(),
    correlationId: uuid(),
    source: 'prueba-de-carga',
    payload: { ticketId: uuid(), titulo, descripcion },
  };
  const respuesta = rabbitmq(
    'POST',
    `/exchanges/%2F/${EXCHANGE}/publish`,
    {
      properties: { delivery_mode: 2, content_type: 'application/json', content_encoding: 'utf-8' },
      routing_key: 'ClasificacionSolicitada',
      payload: JSON.stringify(solicitud),
      payload_encoding: 'string',
    },
    { operacion: 'publicar_solicitud' },
  );
  check(respuesta, { 'la solicitud entra a la cola': (r) => r.status === 200 && r.json('routed') === true });
}

export function muestrearColas() {
  solicitudesEnCola.add(mensajesEn(COLA_SOLICITUDES));
  mensajesMuertos.add(mensajesEn(COLA_MUERTOS));
}

export function teardown(datos) {
  let segundos = 0;
  while (mensajesEn(COLA_SOLICITUDES) > 0 && segundos < ESPERA_MAXIMA_VACIADO_S) {
    sleep(1);
    segundos += 1;
  }
  segundosDeVaciado.add(segundos);

  const muertos = mensajesEn(COLA_MUERTOS) - datos.muertosAlEmpezar;
  if (MODO === 'cola') {
    const respuestas = mensajesEn(COLA_RESPUESTAS);
    rabbitmq('DELETE', `/queues/%2F/${COLA_RESPUESTAS}`);
    console.log(`Respuestas ClasificacionGenerada recibidas: ${respuestas}`);
  }
  console.log(`Solicitudes que terminaron en la cola de mensajes muertos durante la prueba: ${muertos}`);
  console.log(`Segundos hasta que la cola de solicitudes quedo vacia despues de la carga: ${segundos}`);
}
