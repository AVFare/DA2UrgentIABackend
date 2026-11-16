// Prueba de carga basica de UrgentIA con k6. Como correrla: ver README.md de esta carpeta.
//
// MODO=gateway  crea tickets por el api-gateway (POST /api/tickets), como un solicitante, y mide cuánto tarda
//               en llegar la clasificación que se pide por la cola.
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
const COLA_CLASIFICACIONES = 'tickets.clasificaciones';
const COLA_CLASIFICACIONES_MUERTOS = 'tickets.clasificaciones.dlq';
const COLA_RESPUESTAS = 'carga.respuestas';
const ESPERA_MAXIMA_VACIADO_S = 120;
// Uno de cada MUESTRA tickets se consulta hasta que aparece clasificado, como hace el frontend.
const MUESTRA = Number(__ENV.MUESTRA || 10);
const ESPERA_MAXIMA_CLASIFICACION_S = 30;

const TICKETS = [
  ['No puede ingresar nadie', 'Producción caída, todos los usuarios bloqueados en el login desde las 9'],
  ['No puedo pagar con tarjeta', 'Al confirmar el pago con mi tarjeta me da error 500'],
  ['Consulta sobre reportes', '¿Cómo exporto el reporte mensual de ventas a Excel?'],
  ['Alta de usuario nuevo', 'Necesito que den de alta a un empleado nuevo en el sistema con rol de ventas'],
  ['Botón de reporte no anda', 'El botón Descargar PDF del reporte de stock no hace nada en Firefox'],
];

const solicitudesEnCola = new Trend('cola_solicitudes_mensajes');
const mensajesMuertos = new Trend('cola_muertos_mensajes');
const clasificacionesEnCola = new Trend('cola_clasificaciones_mensajes');
const ticketsCreados = new Counter('tickets_creados');
const segundosHastaClasificar = new Trend('ticket_segundos_hasta_clasificar');
const ticketsSinClasificar = new Counter('tickets_sin_clasificar_en_la_muestra');
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
    // RNF02: crear un ticket responde en menos de 8 s.
    'http_req_duration{operacion:crear_ticket}': ['p(95)<8000'],
    // Ningun ticket de la muestra se queda sin clasificar.
    tickets_sin_clasificar_en_la_muestra: ['count==0'],
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
  const datos = {
    muertosAlEmpezar: mensajesEn(COLA_MUERTOS),
    clasificacionesMuertasAlEmpezar: mensajesEn(COLA_CLASIFICACIONES_MUERTOS),
  };
  if (MODO === 'cola') {
    // Alguien tiene que recibir las respuestas: sin una cola enlazada, las solicitudes no se confirman.
    rabbitmq('PUT', `/queues/%2F/${COLA_RESPUESTAS}`, { durable: true, arguments: { 'x-queue-type': 'quorum' } });
    rabbitmq('POST', `/bindings/%2F/e/${EXCHANGE}/q/${COLA_RESPUESTAS}`, { routing_key: 'ClasificacionGenerada' });
    return datos;
  }
  datos.token = iniciarSesion('solicitante@urgentia.local', 'Usuario123!');
  datos.tokenAdmin = iniciarSesion('admin@urgentia.local', 'Admin123!');
  datos.pendientesAlEmpezar = ticketsPendientesDeClasificacion(datos.tokenAdmin);
  return datos;
}

function iniciarSesion(email, password) {
  const login = http.post(`${GATEWAY}/api/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { operacion: 'login' },
  });
  if (login.status !== 200) {
    throw new Error(`No se pudo iniciar sesion como ${email}: HTTP ${login.status}`);
  }
  return login.json('accessToken');
}

function ticketsPendientesDeClasificacion(token) {
  const respuesta = http.get(`${GATEWAY}/api/tickets?estado=PENDIENTE_CLASIFICACION&size=1`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { operacion: 'administracion' },
  });
  return respuesta.status === 200 ? respuesta.json('totalElements') : -1;
}

function esperarClasificacion(id, token, inicio) {
  const limite = inicio + ESPERA_MAXIMA_CLASIFICACION_S * 1000;
  while (Date.now() < limite) {
    sleep(0.2);
    const ticket = http.get(`${GATEWAY}/api/tickets/${id}`, {
      headers: { Authorization: `Bearer ${token}` },
      tags: { operacion: 'consultar_ticket' },
    });
    if (ticket.status === 200 && ticket.json('estado') !== 'PENDIENTE_CLASIFICACION') {
      segundosHastaClasificar.add((Date.now() - inicio) / 1000);
      return;
    }
  }
  ticketsSinClasificar.add(1);
}

export function crearTicket(datos) {
  const [titulo, descripcion] = TICKETS[Math.floor(Math.random() * TICKETS.length)];
  const inicio = Date.now();
  const respuesta = http.post(`${GATEWAY}/api/tickets`, JSON.stringify({ titulo, descripcion }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${datos.token}` },
    tags: { operacion: 'crear_ticket' },
  });
  check(respuesta, { 'el ticket se crea (201)': (r) => r.status === 201 });
  if (respuesta.status !== 201) {
    return;
  }
  ticketsCreados.add(1);
  if (Math.random() * MUESTRA < 1) {
    esperarClasificacion(respuesta.json('id'), datos.token, inicio);
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
  clasificacionesEnCola.add(mensajesEn(COLA_CLASIFICACIONES));
}

export function teardown(datos) {
  let segundos = 0;
  while ((mensajesEn(COLA_SOLICITUDES) > 0 || mensajesEn(COLA_CLASIFICACIONES) > 0)
         && segundos < ESPERA_MAXIMA_VACIADO_S) {
    sleep(1);
    segundos += 1;
  }
  segundosDeVaciado.add(segundos);

  const muertos = mensajesEn(COLA_MUERTOS) - datos.muertosAlEmpezar;
  const clasificacionesMuertas = mensajesEn(COLA_CLASIFICACIONES_MUERTOS) - datos.clasificacionesMuertasAlEmpezar;
  if (MODO === 'cola') {
    const respuestas = mensajesEn(COLA_RESPUESTAS);
    rabbitmq('DELETE', `/queues/%2F/${COLA_RESPUESTAS}`);
    console.log(`Respuestas ClasificacionGenerada recibidas: ${respuestas}`);
  } else {
    // Las colas ya estan vacias: todo lo que se pudo clasificar ya se aplico.
    const pendientes = ticketsPendientesDeClasificacion(datos.tokenAdmin) - datos.pendientesAlEmpezar;
    console.log(`Tickets de la prueba que quedaron en PENDIENTE_CLASIFICACION: ${pendientes}`);
  }
  console.log(`Solicitudes que terminaron en la cola de mensajes muertos durante la prueba: ${muertos}`);
  console.log(`Clasificaciones que terminaron en la cola de mensajes muertos durante la prueba: ${clasificacionesMuertas}`);
  console.log(`Segundos hasta que las colas de clasificacion quedaron vacias despues de la carga: ${segundos}`);
}
