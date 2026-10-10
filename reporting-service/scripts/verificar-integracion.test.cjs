const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { verificarIntegracion } = require('./verificar-integracion.cjs');

async function gatewayDePrueba(
  t,
  {
    vistaAjena = false,
    sinNotificacion = false,
    sinSeguridad = false,
    errorReportes = false,
  } = {},
) {
  let creados = 0,
    consultas = 0;
  const correlaciones = new Set();
  const servidor = createServer(async (req, res) => {
    const ruta = new URL(req.url, 'http://localhost');
    correlaciones.add(req.headers['x-correlation-id']);
    res.setHeader('X-Correlation-Id', req.headers['x-correlation-id']);
    res.setHeader('Content-Type', 'application/json');
    const responder = (body, status = 200) => {
      res.statusCode = status;
      res.end(JSON.stringify(body));
    };
    const token = req.headers.authorization;
    if (ruta.pathname === '/api/auth/login') {
      let body = '';
      for await (const chunk of req) body += chunk;
      const solicitante = JSON.parse(body).email.startsWith('solicitante');
      return responder({
        accessToken: solicitante ? 'solicitante' : 'agente',
        usuario: {
          id: 'usuario-prueba',
          rol: solicitante ? 'SOLICITANTE' : 'AGENTE',
        },
      });
    }
    if (
      ruta.pathname.startsWith('/api/reportes') &&
      token !== 'Bearer agente'
    ) {
      return responder({}, sinSeguridad ? 200 : token ? 403 : 401);
    }
    if (ruta.pathname === '/v3/api-docs/reporting-service') {
      return responder({
        components: { securitySchemes: { bearer: { scheme: 'bearer' } } },
        paths: {
          '/api/reportes/resumen': { get: { security: [{ bearer: [] }] } },
        },
      });
    }
    if (ruta.pathname === '/api/tickets') {
      creados++;
      return responder(
        {
          id: 'ticket-propio',
          prioridad: 'P1',
          estado: 'ESCALADO',
          solicitanteId: 'usuario-prueba',
        },
        201,
      );
    }
    if (ruta.pathname === '/api/reportes/tickets') {
      consultas++;
      if (errorReportes) return responder({}, 500);
      return responder({
        content:
          consultas < 2
            ? []
            : [
                {
                  ticketId: vistaAjena ? 'ticket-ajeno' : 'ticket-propio',
                  prioridad: 'P1',
                  estado: 'ESCALADO',
                  escalado: true,
                  solicitanteId: 'usuario-prueba',
                },
              ],
      });
    }
    if (ruta.pathname === '/api/notificaciones') {
      assert.equal(ruta.searchParams.get('ticketId'), 'ticket-propio');
      return responder({
        content: sinNotificacion
          ? []
          : [
              {
                ticketId: 'ticket-propio',
                tipoEvento: 'TicketEscalado',
                destinatario: 'GRUPO:GUARDIA',
              },
            ],
      });
    }
    if (ruta.pathname === '/api/reportes/resumen')
      return responder({ escalados: 1, porPrioridad: { P1: 1 } });
    if (ruta.pathname === '/api/reportes/sla') return responder({});
    responder({}, 404);
  });
  await new Promise((resolve) => servidor.listen(0, '127.0.0.1', resolve));
  t.after(
    () =>
      new Promise((resolve) => {
        servidor.close(resolve);
        servidor.closeAllConnections();
      }),
  );
  return {
    base: `http://127.0.0.1:${servidor.address().port}`,
    creados: () => creados,
    consultas: () => consultas,
    correlaciones,
  };
}

test('espera la consistencia eventual sin repetir la creacion y conserva un CID libre', async (t) => {
  const gateway = await gatewayDePrueba(t);
  const resultado = await verificarIntegracion(gateway.base, {
    timeoutMs: 2000,
    intervaloMs: 1,
  });
  assert.equal(resultado.ticketId, 'ticket-propio');
  assert.equal(gateway.creados(), 1);
  assert.equal(gateway.consultas(), 2);
  assert.deepEqual([...gateway.correlaciones], [resultado.correlationId]);
});

test('no confunde una proyeccion ajena con el ticket de esta ejecucion', async (t) => {
  const gateway = await gatewayDePrueba(t, { vistaAjena: true });
  await assert.rejects(
    verificarIntegracion(gateway.base, { timeoutMs: 250, intervaloMs: 1 }),
    /P6 no proyecto/,
  );
});

test('falla si no aparece la notificacion de guardia', async (t) => {
  const gateway = await gatewayDePrueba(t, { sinNotificacion: true });
  await assert.rejects(
    verificarIntegracion(gateway.base, { timeoutMs: 250, intervaloMs: 1 }),
    /P5 no notifico/,
  );
});

test('detecta reportes accesibles sin JWT antes de crear datos', async (t) => {
  const gateway = await gatewayDePrueba(t, { sinSeguridad: true });
  await assert.rejects(verificarIntegracion(gateway.base), /HTTP inesperado/);
  assert.equal(gateway.creados(), 0);
});

test('un error HTTP no se oculta como demora de la proyeccion', async (t) => {
  const gateway = await gatewayDePrueba(t, { errorReportes: true });
  await assert.rejects(verificarIntegracion(gateway.base), /HTTP inesperado/);
  assert.equal(gateway.consultas(), 1);
});
