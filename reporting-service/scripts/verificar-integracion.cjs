// Prueba sobre un Compose de desarrollo con LLM_PROVIDER=mock y usuarios semilla.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const { setTimeout: esperar } = require('node:timers/promises');

async function verificarIntegracion(
  base = 'http://localhost:8080',
  { timeoutMs = 60000, intervaloMs = 500 } = {},
) {
  const correlationId = `p6-integracion-${randomUUID()}`;
  async function llamar(ruta, { token, body, status = 200 } = {}) {
    const respuesta = await fetch(new URL(ruta, base), {
      method: body ? 'POST' : 'GET',
      headers: {
        'X-Correlation-Id': correlationId,
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
      signal: AbortSignal.timeout(10000),
    });
    assert.equal(respuesta.status, status, `${ruta}: HTTP inesperado`);
    assert.equal(
      respuesta.headers.get('x-correlation-id'),
      correlationId,
      `${ruta}: correlacion perdida`,
    );
    return respuesta.json();
  }
  async function login(email, password, rol) {
    const sesion = await llamar('/api/auth/login', {
      body: { email, password },
    });
    assert.ok(sesion.accessToken, 'El login debe devolver un JWT');
    assert.equal(sesion.usuario.rol, rol);
    return sesion;
  }
  const solicitante = await login(
    'solicitante@urgentia.local',
    'Usuario123!',
    'SOLICITANTE',
  );
  const agente = await login('guardia@urgentia.local', 'Agente123!', 'AGENTE');
  await llamar('/api/reportes/resumen', { status: 401 });
  await llamar('/api/reportes/resumen', {
    token: solicitante.accessToken,
    status: 403,
  });

  const documento = await llamar('/v3/api-docs/reporting-service');
  assert.equal(documento.components.securitySchemes.bearer.scheme, 'bearer');
  assert.deepEqual(documento.paths['/api/reportes/resumen'].get.security, [
    { bearer: [] },
  ]);

  const ticket = await llamar('/api/tickets', {
    token: solicitante.accessToken,
    status: 201,
    body: {
      titulo: `Caida de produccion ${correlationId}`,
      descripcion:
        'Produccion caida: no funciona el login y todos los usuarios estan bloqueados.',
    },
  });
  assert.ok(ticket.id, 'El ticket creado debe tener id');
  assert.equal(
    ticket.prioridad,
    'P1',
    'Usar LLM_PROVIDER=mock para esta prueba',
  );
  assert.equal(ticket.estado, 'ESCALADO');
  assert.equal(ticket.solicitanteId, solicitante.usuario.id);

  // La entrega HTTP es asincronica: no crear otro ticket al esperar su proyeccion.
  const limite = Date.now() + timeoutMs;
  let proyectado = false,
    notificado = false;
  do {
    const [reportes, notificaciones] = await Promise.all([
      llamar('/api/reportes/tickets?prioridad=P1&estado=ESCALADO&size=100', {
        token: agente.accessToken,
      }),
      llamar(
        `/api/notificaciones?ticketId=${encodeURIComponent(ticket.id)}&size=100`,
        { token: agente.accessToken },
      ),
    ]);
    proyectado = reportes.content.some(
      (vista) =>
        vista.ticketId === ticket.id &&
        vista.estado === 'ESCALADO' &&
        vista.prioridad === 'P1' &&
        vista.escalado === true &&
        vista.solicitanteId === solicitante.usuario.id,
    );
    notificado = notificaciones.content.some(
      (notificacion) =>
        notificacion.ticketId === ticket.id &&
        notificacion.tipoEvento === 'TicketEscalado' &&
        notificacion.destinatario === 'GRUPO:GUARDIA',
    );
    if (proyectado && notificado) break;
    await esperar(intervaloMs);
  } while (Date.now() < limite);
  assert.ok(
    proyectado,
    `P6 no proyecto el ticket ${ticket.id} dentro del plazo`,
  );
  assert.ok(
    notificado,
    `P5 no notifico a guardia por el ticket ${ticket.id} dentro del plazo`,
  );
  const resumen = await llamar('/api/reportes/resumen', {
    token: agente.accessToken,
  });
  assert.ok(resumen.escalados >= 1 && resumen.porPrioridad.P1 >= 1);
  await llamar('/api/reportes/sla', { token: agente.accessToken });
  return { ticketId: ticket.id, correlationId, proyectado, notificado };
}

module.exports = { verificarIntegracion };
if (require.main === module) {
  verificarIntegracion(process.argv[2]).then(
    (resultado) =>
      console.log('Integracion verificada:', JSON.stringify(resultado)),
    (error) => {
      console.error(error.message);
      process.exitCode = 1;
    },
  );
}
