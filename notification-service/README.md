# notification-service

Consume los eventos de dominio de `ticket-service` y registra notificaciones (email simulado o internas).
Dueño: P5. Puerto **8084**. Base MongoDB `notifications_db`.
Stack: Python 3.12, FastAPI, Pydantic 2 (pydantic-settings) y PyMongo.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/eventos` | Recibe el sobre de evento (solo red interna). `202` con `PROCESADO`, `DUPLICADO` o `IGNORADO`; `400 VALIDACION` si el sobre no cumple el esquema |
| `GET` | `/api/notificaciones?ticketId=&destinatario=&page=0&size=20` | Página de notificaciones, de la más nueva a la más vieja |
| `GET` | `/api/notificaciones/{id}` | Una notificación, o `404 NO_ENCONTRADO` |
| `GET` | `/health` | `{ "status": "UP", "service": "notification-service", "version": "0.1.0" }` |

OpenAPI en `/openapi.json` y Swagger UI en `/docs`. El contrato exportado está en `contracts/notification-service.yaml`.

### Reglas

| Evento | Destinatario | Canal | Asunto |
|---|---|---|---|
| `TicketEscalado` | `GRUPO:GUARDIA` | `EMAIL_SIMULADO` | `[P1] Ticket escalado: {titulo}` |
| `TicketAsignado` | `USUARIO:{agenteAsignadoId}` | `INTERNA` | `Se te asignó el ticket: {titulo}` |
| `TicketResuelto` | `USUARIO:{solicitanteId}` | `EMAIL_SIMULADO` | `Tu ticket fue resuelto: {titulo}` |
| Otros | — | — | `IGNORADO` |

`EMAIL_SIMULADO` escribe una línea de log JSON con destinatario, asunto y mensaje. `INTERNA` solo se guarda.

### Idempotencia

La entrega es "al menos una vez". Cada evento se registra en `eventos_procesados` (índice único en `eventId`) antes de procesarlo; si ya estaba, responde `202 DUPLICADO` sin efectos.
Si algo falla a mitad de camino, el registro se borra para que el reintento de `ticket-service` lo procese.

## Estructura

```
app/
  main.py            arma la app: routers, handlers, middleware e índices al arrancar
  config.py          Settings: única lectura del entorno
  db.py              cliente de Mongo
  logging_config.py  logs JSON con correlationId
  middleware/        X-Correlation-Id
  schemas/           todo lo que entra y sale de la API (camelCase en el JSON)
  endpoints/         rutas: reciben, delegan en services y responden
  services/          lógica: reglas, idempotencia, plantillas y canales
  repositories/      únicos que tocan Mongo (base común: MongoRepository)
  providers/         lo externo: EmailProvider
  exceptions/        ApiException y formato común de error
tests/               pytest con mongomock (no hace falta un Mongo levantado)
```

Quién puede usar a quién: endpoints → services → repositories → Mongo. Los endpoints no tocan repositories y los services no reciben schemas, sino dicts.

## Cómo correr

Con todo el sistema, desde la raíz del repo:

```bash
docker compose up --build notification-service
```

Local, contra un Mongo propio (desde `notification-service/`):

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
MONGO_URI=mongodb://localhost:27017 uvicorn app.main:app --port 8084 --reload
```

Variables: `MONGO_URI`, `MONGO_DB` (default `notifications_db`), `LOG_LEVEL` (default `INFO`), `SERVICE_VERSION` (default `0.1.0`).

Tests y lint:

```bash
pytest
ruff check . && ruff format --check .
```

Regenerar el contrato después de cambiar la API:

```bash
python scripts/export_openapi.py
```

## Patrones aplicados

| Patrón | Dónde | Problema que resuelve |
|---|---|---|
| Observer (consumidor) | `POST /api/eventos` + `REGLAS` en `app/services/evento_service.py` | El servicio reacciona a los eventos del ticket sin que `ticket-service` sepa quién notifica ni cómo. Sumar un evento es sumar una entrada en `REGLAS` |
| Strategy | `app/services/canales.py` (`Canal`, `EmailSimulado`, `Interna`) | Cambiar o sumar canales (en la parte 2, un envío real) sin tocar la lógica de eventos |
| Factory | `app/services/plantillas.py` (`PlantillaFactory`) | Arma asunto y mensaje según el tipo de evento en un solo lugar |
| Repository | `app/repositories/` (`MongoRepository` y sus hijas) | Solo los repositories conocen Mongo; la lógica común (paginar, buscar por id, convertir `_id`) está una sola vez |
