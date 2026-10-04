# reporting-service · P6

Servicio de lectura de TriageDesk. Recibe los seis eventos de `ticket-service`,
mantiene una vista en MongoDB y expone resumen, SLA y tickets. No calcula
prioridades ni consulta bases de otros servicios.

## Ejecutar P6 sin los otros servicios

Requisitos: Node **20.x** y npm. Desde esta carpeta:

```bash
npm ci
npm run demo
```

La demo descarga MongoDB 7 la primera vez, inicia una instancia real temporal,
compila el servicio y carga un ticket escalado con SLA vencido. Mantiene la API
en `http://localhost:8085` hasta pulsar Ctrl+C. Los datos de esta modalidad se
descartan al terminar; no usa ni modifica el MongoDB del equipo.

- Swagger: http://localhost:8085/api-docs/
- OpenAPI JSON: http://localhost:8085/api-docs-json
- Health: http://localhost:8085/health
- Resumen: http://localhost:8085/api/reportes/resumen
- SLA: http://localhost:8085/api/reportes/sla
- Tickets: http://localhost:8085/api/reportes/tickets?prioridad=P1&estado=ESCALADO

## Ejecutar con Docker

Desde esta carpeta, con Docker Desktop funcionando (y su integración WSL
activada si usás esta terminal):

```bash
docker compose -f compose.local.yml up --build
```

Es un entorno exclusivo de P6. Publica la API en `127.0.0.1:8085` y MongoDB en
`127.0.0.1:27018`. Los datos persisten en un volumen. No inicia los otros servicios
ni cambia el Compose raíz. Para detenerlo conservando los datos:

```bash
docker compose -f compose.local.yml down
```

El Dockerfile construye en dos etapas, instala solo dependencias de producción
en la imagen final, ejecuta como usuario `node` y comprueba `/health`.

## Ejecutar con el MongoDB del equipo

```bash
npm ci
npm run build
cp .env.example .env
# Editar MONGO_URI con las credenciales reales de reporting_user.
node --env-file=.env dist/main.js
```

Con variables ya exportadas podés usar `npm start` o `npm run start:dev`.
`MONGO_URI` es obligatoria; la base acordada es `reporting_db`. `LOG_LEVEL`
acepta `DEBUG`, `INFO`, `WARN` o `ERROR` y por defecto usa `INFO`.

P1 debe incorporar el servicio al Compose compartido con `build: ./reporting-service`,
`MONGO_URI=mongodb://reporting_user:<password>@mongo:27017/reporting_db` y la
red `urgentia-net` que existe actualmente en el repositorio. La URL interna es
`http://reporting-service:8085`. En ese despliegue solo el gateway publica el
acceso externo; `/api/eventos` permanece exclusivamente en la red interna.

## API

| Método | Ruta                    | Resultado                                                  |
| ------ | ----------------------- | ---------------------------------------------------------- |
| POST   | `/api/eventos`          | 202: `{ eventId, resultado: "PROCESADO" / "DUPLICADO" }`   |
| GET    | `/api/reportes/resumen` | Totales y distribuciones completas, incluso grupos en cero |
| GET    | `/api/reportes/sla`     | Tickets vencidos y cumplimiento por P1–P4                  |
| GET    | `/api/reportes/tickets` | Vista filtrada por prioridad/estado y paginada             |
| GET    | `/health`               | 200 si Mongo responde al ping; 503 si no está disponible   |

La paginación usa `page=0`, `size=20` por defecto y admite hasta 100 elementos.
Ordena por fecha de creación descendente y `ticketId` para desempatar. Los
filtros vacíos se omiten; los valores inválidos devuelven 400 `VALIDACION`.
La respuesta nunca expone `_id`, el evento almacenado ni datos internos.

El gateway valida JWT y limita reportes a AGENTE/ADMIN. Este servicio confía
en la red interna, según el contrato del proyecto. Toda respuesta propaga
`X-Correlation-Id`; si falta, genera uno. Los logs son JSON y el procesamiento
del evento usa además su `correlationId`. Los errores siguen el formato común.

## Proyecciones e idempotencia

1. Valida UUID v4, fechas UTC, versión 1, enums y snapshot completo. Rechaza
   campos extra (incluida `descripcion`). `estadoAnterior` es obligatorio en
   cambios de estado, escalados y resueltos; `motivo` lo es en escalados.
2. Guarda el evento original como `PENDIENTE` en `eventos_procesados`, con un
   índice único por `eventId`. No reemplaza el cuerpo en reintentos.
3. Elige el proyector por tipo de evento y hace un upsert atómico en `ticket_view`,
   con índice único por `ticketId`. La comparación de `occurredAt` con
   `ultimoEventoEn` ocurre dentro de MongoDB, junto con la actualización.
4. Marca `PROCESADO` después de la proyección. Los duplicados ya completados
   no producen efectos. Si falla, devuelve 500 y conserva el evento pendiente
   para que el productor pueda reintentar.

Funciona con MongoDB standalone; no exige transacciones ni replica set. Si se
interrumpe entre proyección y confirmación, volver a aplicar el evento es seguro.
La recuperación depende del reintento HTTP del productor; no hay un worker de
recuperación automática en esta entrega.

Un evento anterior no sobrescribe la vista. Un evento distinto con la misma
fecha se admite: el sobre no incluye una secuencia que permita ordenar empates.
Los títulos se guardan como literales, incluso si empiezan con `$`.

## Criterios de los reportes

- Abierto: estado diferente de `RESUELTO` y `CERRADO`.
- Escalado: estado actual `ESCALADO`; no cuenta como historial de escalamiento.
- SLA vencido: límite no nulo, estrictamente anterior a ahora y ticket abierto.
- En término: `fechaResolucion <= fechaLimiteSla`.
- Cumplimiento: tickets actualmente resueltos o cerrados, con prioridad y ambas
  fechas conocidas. Cada prioridad aparece; sin resueltos el porcentaje es `null`.
- Horas vencidas: una cifra decimal; porcentaje: hasta dos cifras decimales.
- La resolución usa `occurredAt` al recibir un snapshot `RESUELTO`. Se conserva
  al cerrar, se limpia al reabrir y se registra nuevamente al resolver.

El snapshot acordado no contiene `fechaResolucion`. Si el primer snapshot
recibido ya está `CERRADO` y la resolución llega después como evento antiguo,
esa fecha permanece desconocida y el ticket no entra en el porcentaje. La
vista respeta la regla de no sobrescribir con eventos viejos. Ver la propuesta
de coordinación en [docs/estado-trello.md](docs/estado-trello.md).

## Patrones aplicados

| Patrón         | Archivos                                                                                | Problema que resuelve                               |
| -------------- | --------------------------------------------------------------------------------------- | --------------------------------------------------- |
| CQRS (lectura) | `eventos/`, `reportes/`, `schemas/ticket-view.schema.ts`                                | Separa la vista de consulta del agregado de tickets |
| Strategy       | `proyecciones/proyector.interface.ts`, `proyectores.ts`                                 | Selecciona un proyector por cada tipo de evento     |
| Repository     | `ticket-view.repository.ts`, `evento-procesado.repository.ts`, `reportes.repository.ts` | Encapsula persistencia y consultas MongoDB          |

## Validar

```bash
npm run build
npm run typecheck
npm test
npm run test:e2e
npm run format:check
npm run verificar:demo
```

Los tests de integración usan MongoDB 7 real y temporal, sin Docker. Comprueban
concurrencia, eventos desordenados, reintentos, reaperturas, validación, SLA,
paginación, health y el contrato Swagger. Requieren red en la primera ejecución
para descargar MongoDB. `npm run verificar:demo` levanta el JavaScript compilado,
prueba la API, guarda respuestas reales en `docs/evidencias.json` y apaga ambos
procesos. El puerto 8085 debe estar libre.

## Documentación y defensa

- [Contrato OpenAPI](../contracts/reporting-service.yaml).
- [Diagrama de componentes](../docs/diagrams/componentes-reporting.puml).
- [Cobertura de las tarjetas de Trello y pendientes](docs/estado-trello.md).
- [Borrador del informe de P6](docs/informe-p6.md).
- [Guion y ensayos](docs/defensa-p6.md).
- [Diapositivas](docs/slides-p6.md).

Para exportar los borradores y las evidencias a PDF:

```bash
python3 -m pip install -r scripts/requirements-docs.txt
python3 scripts/exportar-defensa.py
```

El material incluye los nombres proporcionados por Francisco. Las asignaciones
de los demás integrantes, su revisión y el informe final del equipo siguen
pendientes de confirmación. El esquema de eventos compartido pertenece a P2/P5;
P6 aporta [su propuesta local](docs/eventos-consumidos.schema.json) sin modificarlo.
