const { MongoMemoryServer } = require('mongodb-memory-server');
const { spawn } = require('node:child_process');
const { randomUUID } = require('node:crypto');
const { createServer } = require('node:net');
const { writeFileSync } = require('node:fs');
const { resolve } = require('node:path');
const assert = require('node:assert/strict');

const verificar = process.argv.includes('--verificar');
const base = 'http://127.0.0.1:8085';
let mongo, servicio;
let cerrando = false;
const errores = [];

async function cerrar() {
  if (cerrando) return;
  cerrando = true;
  if (servicio && servicio.exitCode === null && servicio.signalCode === null) {
    const terminado = new Promise((resolve) => servicio.once('exit', resolve));
    servicio.kill('SIGTERM');
    const limite = setTimeout(() => servicio.kill('SIGKILL'), 10000);
    await terminado;
    clearTimeout(limite);
  }
  await mongo?.stop();
}

async function comprobarPuerto() {
  const prueba = createServer();
  await new Promise((resolve, reject) => {
    prueba.once('error', () =>
      reject(
        new Error(
          'El puerto 8085 esta ocupado. Detene el otro servicio antes de iniciar la demo.',
        ),
      ),
    );
    prueba.listen(8085, '0.0.0.0', () => prueba.close(resolve));
  });
}

async function iniciar() {
  await comprobarPuerto();
  mongo = await MongoMemoryServer.create({
    binary: { version: '7.0.24' },
    instance: { dbName: 'reporting_db' },
  });
  servicio = spawn(process.execPath, [resolve(__dirname, '../dist/main.js')], {
    cwd: resolve(__dirname, '..'),
    env: {
      ...process.env,
      MONGO_URI: mongo.getUri('reporting_db'),
      LOG_LEVEL: verificar ? 'ERROR' : 'INFO',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  servicio.stderr.on('data', (data) => {
    errores.push(data.toString());
    if (!verificar) process.stderr.write(data);
  });
  servicio.stdout.on('data', (data) => {
    if (!verificar) process.stdout.write(data);
  });
  console.log('Iniciando reporting-service con MongoDB temporal...');
  // En WSL, las dependencias ubicadas en /mnt/c pueden tardar en cargar.
  const hasta = Date.now() + 120000;
  while (Date.now() < hasta) {
    if (servicio.exitCode !== null)
      throw new Error(
        `El servicio termino antes de iniciar: ${errores.join('')}`,
      );
    try {
      const r = await fetch(`${base}/health`, {
        signal: AbortSignal.timeout(1000),
      });
      if (r.ok) return;
    } catch {
      /* Esperar al arranque del proceso compilado. */
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error('El servicio no inicio en 120 segundos');
}

async function main() {
  await iniciar();
  const resultados = [];
  async function llamar(nombre, ruta, body, estadoEsperado = 200) {
    const respuesta = await fetch(`${base}${ruta}`, {
      method: body ? 'POST' : 'GET',
      headers: {
        'Content-Type': 'application/json',
        'X-Correlation-Id': 'demo-p6',
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
      signal: AbortSignal.timeout(5000),
    });
    const json = await respuesta.json();
    assert.equal(respuesta.status, estadoEsperado, nombre);
    resultados.push({
      nombre,
      metodo: body ? 'POST' : 'GET',
      ruta,
      status: respuesta.status,
      respuesta: json,
    });
    return json;
  }
  const ahora = Date.now();
  const iso = (ms) => new Date(ms).toISOString();
  const creado = {
    eventId: randomUUID(),
    eventType: 'TicketCreado',
    version: 1,
    occurredAt: iso(ahora - 7200000),
    correlationId: randomUUID(),
    source: 'ticket-service',
    payload: {
      ticket: {
        ticketId: randomUUID(),
        titulo: 'Produccion caida (demo P6)',
        estado: 'NUEVO',
        prioridad: null,
        categoria: null,
        moduloAfectado: null,
        solicitanteId: randomUUID(),
        agenteAsignadoId: null,
        fechaCreacion: iso(ahora - 7200000),
        fechaLimiteSla: null,
      },
    },
  };
  const escalado = {
    ...creado,
    eventId: randomUUID(),
    eventType: 'TicketEscalado',
    occurredAt: iso(ahora - 7199000),
    payload: {
      ticket: {
        ...creado.payload.ticket,
        estado: 'ESCALADO',
        prioridad: 'P1',
        categoria: 'INCIDENTE',
        moduloAfectado: 'AUTENTICACION',
        fechaLimiteSla: iso(ahora - 3599000),
      },
      estadoAnterior: 'CLASIFICADO',
      motivo: 'Prioridad P1',
    },
  };
  await llamar('Health', '/health');
  assert.equal(
    (await llamar('Creacion', '/api/eventos', creado, 202)).resultado,
    'PROCESADO',
  );
  assert.equal(
    (await llamar('Escalamiento', '/api/eventos', escalado, 202)).resultado,
    'PROCESADO',
  );
  assert.equal(
    (await llamar('Duplicado', '/api/eventos', escalado, 202)).resultado,
    'DUPLICADO',
  );
  const viejo = { ...creado, eventId: randomUUID() };
  await llamar('Evento antiguo', '/api/eventos', viejo, 202);
  const resumen = await llamar('Resumen del escalado', '/api/reportes/resumen');
  assert.equal(resumen.total, 1);
  assert.equal(resumen.escalados, 1);
  assert.equal(resumen.slaVencidos, 1);
  const sla = await llamar('SLA vencido', '/api/reportes/sla');
  assert.equal(sla.vencidos.length, 1);
  const tickets = await llamar(
    'Tickets filtrados',
    '/api/reportes/tickets?prioridad=P1&estado=ESCALADO',
  );
  assert.equal(tickets.content[0].estado, 'ESCALADO');
  assert.equal(tickets.totalElements, 1);
  if (verificar) {
    const resuelto = {
      ...escalado,
      eventId: randomUUID(),
      eventType: 'TicketResuelto',
      occurredAt: iso(Date.now()),
      payload: {
        ticket: { ...escalado.payload.ticket, estado: 'RESUELTO' },
        estadoAnterior: 'EN_CURSO',
      },
    };
    await llamar('Resolucion fuera de termino', '/api/eventos', resuelto, 202);
    const cumplimiento = await llamar(
      'Cumplimiento tras resolver',
      '/api/reportes/sla',
    );
    assert.equal(cumplimiento.vencidos.length, 0);
    assert.deepEqual(cumplimiento.cumplimientoPorPrioridad.P1, {
      resueltos: 1,
      enTermino: 0,
      porcentaje: 0,
    });
    writeFileSync(
      resolve(__dirname, '../docs/evidencias.json'),
      JSON.stringify(
        {
          generadoEn: new Date().toISOString(),
          entorno: {
            node: process.version,
            mongo: '7.0.24',
            modalidad:
              'JavaScript compilado + proceso MongoDB real temporal standalone; sin servicios P1-P5',
          },
          resultados,
        },
        null,
        2,
      ) + '\n',
    );
    console.log(
      'Demo verificada: 10 respuestas HTTP reales guardadas en docs/evidencias.json.',
    );
    await cerrar();
  } else {
    console.log('\nDemo P6 lista: http://localhost:8085/api-docs/');
    console.log(
      'Ticket escalado con SLA vencido. Ctrl+C detiene el servicio y elimina los datos temporales.',
    );
    servicio.once('exit', async () => {
      if (!cerrando) {
        await cerrar();
        process.exitCode = 1;
      }
    });
  }
}

process.once('SIGINT', () => {
  void cerrar();
});
process.once('SIGTERM', () => {
  void cerrar();
});
main().catch(async (error) => {
  console.error(error.message);
  process.exitCode = 1;
  await cerrar();
});
