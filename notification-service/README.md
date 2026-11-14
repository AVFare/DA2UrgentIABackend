# notification-service

Consume los eventos de dominio de `ticket-service` y registra notificaciones (email simulado o internas).
Los eventos llegan por RabbitMQ (cola `notificaciones.eventos`, ver `contracts/messaging/`) y, mientras dure la transición, también por `POST /api/eventos`.
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

`INTERNA` solo se guarda. Para `EMAIL_SIMULADO`, `EmailProvider` decide según `EMAIL_PROVIDER`:

| `EMAIL_PROVIDER` | Qué hace |
|---|---|
| `simulado` (default, para el CI) | Escribe una línea de log JSON (`EMAIL_SIMULADO`) con destinatario, asunto y mensaje |
| `real` | Manda el mail por SMTP con TLS (Gmail: `smtp.gmail.com:587` y una contraseña de aplicación) y lo loguea como `EMAIL_ENVIADO` |

Los usuarios de la demo no tienen casillas reales, así que en modo `real` todo va a `EMAIL_DESTINO_DEMO`, con el destinatario original al principio del asunto: `[GRUPO:GUARDIA] [P1] Ticket escalado: …`.
Si el servidor SMTP falla, la notificación se guarda como `FALLIDA` y el evento se da por procesado: no se pierde ni se reintenta en bucle.
Con `EMAIL_PROVIDER=real`, el servicio no arranca si faltan `SMTP_USER`, `SMTP_PASSWORD` o `EMAIL_DESTINO_DEMO`.

### Consumidor de RabbitMQ

Si está `RABBITMQ_URL`, el servicio escucha la cola `notificaciones.eventos` en un hilo propio, junto con la API. Sin esa variable no se levanta y queda solo `POST /api/eventos`.

| Caso | Respuesta al broker |
|---|---|
| Evento procesado, repetido o que no notifica | `ack` |
| Sobre inválido (no cumple el esquema) o `TicketAsignado` sin agente | `nack` sin reencolar: va a `notificaciones.eventos.dlq` |
| Falla transitoria (Mongo caído, error inesperado) | `nack` con reencolado; después de 5 reintentos RabbitMQ lo manda a la dead-letter |

- El `ack` se manda recién después de guardar la notificación.
- Si RabbitMQ se cae o la cola todavía no existe, el consumidor reintenta cada 5 segundos y se reconecta solo.
- Los logs de cada mensaje llevan el `correlationId` del sobre.

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
  consumers/         consumidor de RabbitMQ: otra entrada, que también delega en services
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

Variables: `MONGO_URI`, `MONGO_DB` (default `notifications_db`), `RABBITMQ_URL` (sin valor no se levanta el consumidor), `RABBITMQ_COLA` (default `notificaciones.eventos`), `RABBITMQ_PREFETCH` (default `10`), `EMAIL_PROVIDER` (`simulado` o `real`), `SMTP_HOST` (default `smtp.gmail.com`), `SMTP_PORT` (default `587`), `SMTP_USER`, `SMTP_PASSWORD` (solo en el `.env`), `EMAIL_REMITENTE` (default `SMTP_USER`), `EMAIL_DESTINO_DEMO`, `LOG_LEVEL` (default `INFO`), `SERVICE_VERSION` (default `0.1.0`).

Tests y lint:

```bash
pytest
ruff check . && ruff format --check .
```

Las pruebas contra un RabbitMQ real se saltean salvo que se indique uno, con la topología de `contracts/messaging/` cargada:

```bash
RABBITMQ_URL_TEST=amqp://urgentia:rabbit_pass@localhost:5672 pytest tests/test_consumidor_rabbitmq.py
```

Regenerar el contrato después de cambiar la API:

```bash
python scripts/export_openapi.py
```

## Patrones aplicados

| Patrón | Dónde | Problema que resuelve |
|---|---|---|
| Observer (consumidor) | Cola `notificaciones.eventos` (`app/consumers/eventos_consumer.py`) y `POST /api/eventos`, más `REGLAS` en `app/services/evento_service.py` | El servicio reacciona a los eventos del ticket sin que `ticket-service` sepa quién notifica ni cómo. Sumar un evento es sumar una entrada en `REGLAS` |
| Strategy | `app/services/canales.py` (`Canal`, `EmailSimulado`, `Interna`) y `EMAIL_PROVIDER` en `EmailProvider` | Cambiar o sumar canales, o pasar del mail simulado al real, sin tocar la lógica de eventos |
| Factory | `app/services/plantillas.py` (`PlantillaFactory`) | Arma asunto y mensaje según el tipo de evento en un solo lugar |
| Repository | `app/repositories/` (`MongoRepository` y sus hijas) | Solo los repositories conocen Mongo; la lógica común (paginar, buscar por id, convertir `_id`) está una sola vez |
