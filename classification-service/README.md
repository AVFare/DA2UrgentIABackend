# classification-service

Clasifica el texto de un ticket con un LLM: categoría, urgencia, impacto, módulo afectado,
si requiere escalamiento, confianza y justificación. La IA sugiere y el dominio decide:
este servicio nunca devuelve la prioridad.

| | |
|---|---|
| Puerto | 8082 |
| Tecnología | Python 3.12, FastAPI, Pydantic |
| Base de datos | MongoDB `classification_db`, colección `clasificaciones` |
| Contrato | [`contracts/classification-service.yaml`](../contracts/classification-service.yaml) |
| Swagger propio | http://localhost:8082/docs (OpenAPI en `/openapi.json`) |
| Dueño | P4 |

## Cómo funciona

`POST /api/clasificaciones` pasa por `ClasificacionFacade`:

1. Enmascara emails, teléfonos y DNI del título y la descripción.
2. Arma el prompt desde `prompts/clasificacion_{PROMPT_VERSION}.txt`.
3. Consulta al LLM con timeout `LLM_TIMEOUT_MS`. Si la respuesta no es una clasificación válida
   y quedan al menos 2 s del presupuesto total de 6 s, reintenta una vez.
4. Guarda en MongoDB la clasificación, el texto enmascarado y la respuesta cruda (también
   enmascarada), con proveedor, modelo, versión del prompt y latencia.

Errores: respuesta inválida o LLM con error → 502 `LLM_RESPUESTA_INVALIDA`; sin respuesta a
tiempo → 504 `LLM_TIMEOUT`.

## Clasificación por cola

Además de la API, el servicio clasifica lo que le llega por RabbitMQ. El contrato está en
[`contracts/messaging/`](../contracts/messaging/README.md) (topología y reglas) y en
[`contracts/events/clasificacion-events.schema.json`](../contracts/events/clasificacion-events.schema.json)
(los mensajes). El flujo completo, con los casos de falla, está en el diagrama
[`secuencia-clasificacion-por-cola`](../docs/diagrams/secuencia-clasificacion-por-cola.png).

1. Consume `ClasificacionSolicitada` de la cola `clasificacion.solicitudes`, hasta `COLA_PREFETCH`
   a la vez.
2. Clasifica con la misma `ClasificacionFacade` que usa la API y guarda el registro con el id de
   la solicitud.
3. Publica `ClasificacionGenerada` en `urgentia.clasificacion` (routing key `ClasificacionGenerada`)
   con resultado `CLASIFICADO` y la clasificación, o con resultado `ERROR` si el LLM falló.
4. Confirma la solicitud (`ack`) después de guardar y publicar.

El servicio declara al arrancar su parte de la topología (exchange, cola, cola de mensajes muertos
y bindings) con los mismos argumentos que `rabbitmq-definitions.json`, así puede consumir aunque la
topología todavía no se haya cargado.

| Situación | Qué pasa |
|---|---|
| Llega dos veces la misma solicitud | No se consulta otra vez al LLM: se publica de nuevo la clasificación guardada, con el mismo `eventId` |
| El LLM falla o no responde | Se publica `ClasificacionGenerada` con resultado `ERROR` y el código del REST (`LLM_TIMEOUT` o `LLM_RESPUESTA_INVALIDA`), y la solicitud se confirma. Se puede volver a pedir la clasificación |
| El mensaje no cumple el contrato | Va directo a `clasificacion.solicitudes.dlq`, sin reintentos |
| Falla la base o la publicación (ninguna cola recibe la respuesta) | La solicitud vuelve a la cola. Tras 5 reintentos, RabbitMQ la manda a `clasificacion.solicitudes.dlq` |
| El servicio se cae a mitad de una solicitud | Como no llegó a confirmarla, RabbitMQ la vuelve a entregar |
| RabbitMQ no está disponible | La API sigue respondiendo y la conexión se reintenta cada 5 s |

Sin `RABBITMQ_URL` el servicio no consume la cola y atiende solo por la API.

Las colas y los mensajes se ven en la consola de RabbitMQ: http://localhost:15672.

## Cómo correrlo sin Docker

Desde esta carpeta, con Python 3.12. El servicio necesita MongoDB: se puede usar el del
Compose (`docker compose up -d --wait mongo` desde la raíz del repo).

```bash
python -m venv .venv
.venv/Scripts/activate            # Windows (Git Bash). En Linux/macOS: source .venv/bin/activate
pip install -r requirements-dev.txt
pytest
MONGO_URI="mongodb://classification_user:classification_pass@localhost:27017/classification_db" python -m app.main
```

Por defecto usa `LLM_PROVIDER=mock`, que no necesita internet ni API key.

Los tests del repositorio corren contra un MongoDB real solo si se define `MONGO_URI_TEST`, y
los de la cola contra un RabbitMQ real solo si se define `RABBITMQ_URL_TEST`:

```bash
MONGO_URI_TEST="mongodb://root:mongo@localhost:27017/?authSource=admin" pytest tests/test_mongo_repository.py
RABBITMQ_URL_TEST="amqp://urgentia:rabbitmq_pass@localhost:5672/" pytest tests/test_rabbitmq_worker.py
```

## Cómo correrlo con Docker

Desde la raíz del repo, con el `.env` creado a partir de `.env.example`:

```bash
docker compose up -d --build --wait classification-service
docker compose logs classification-service
```

Levanta también MongoDB y RabbitMQ, de los que depende. Dentro de la red del Compose el servicio responde en
`http://classification-service:8082`; desde afuera se accede a través del api-gateway.

## Contrato

El contrato se genera desde el código. Si cambia la API, se vuelve a exportar y se commitea
junto con el cambio:

```bash
python -m scripts.exportar_openapi
```

Cambiar el contrato requiere avisar en el grupo y la aprobación de los dueños de los servicios
que lo consumen.

## Proveedores de LLM

| `LLM_PROVIDER` | Adapter | URL base por defecto | Modelo por defecto | API key |
|---|---|---|---|---|
| `mock` | `MockLlmProvider` | — | `mock-v1` | No |
| `ollama` | `OpenAICompatibleProvider` | `http://host.docker.internal:11434/v1` | `qwen2.5:1.5b` | No |
| `groq` | `OpenAICompatibleProvider` | `https://api.groq.com/openai/v1` | `qwen/qwen3.8-27b` | Sí |

**Ollama** (modelo local; no sale nada a internet):

```bash
ollama pull qwen2.5:1.5b
# en el .env: LLM_PROVIDER=ollama
docker compose up -d --build --wait classification-service
```

Al arrancar, el servicio precalienta el modelo con el prompt en segundo plano: en CPU, cargar el
modelo y procesar la parte fija del prompt tarda varios segundos. Sin Docker, agregar
`LLM_BASE_URL=http://localhost:11434/v1`.

**Groq** (API en internet): sacar una key gratis en console.groq.com y ponerla en el `.env`
(`LLM_PROVIDER=groq`, `LLM_API_KEY=...`).

## Prompts

| `PROMPT_VERSION` | Uso |
|---|---|
| `full` | Default. Criterios detallados de impacto y escalamiento; mejor resultado con el proveedor real |
| `lite` | Para modelos locales chicos (por ejemplo, `qwen2.5:1.5b` en CPU): más corto, responde más rápido |

## Evaluación (RIA01)

`tests/data/tickets_eval.json` tiene 20 tickets etiquetados. El evaluador los clasifica con el
proveedor configurado, por el mismo camino que en producción, y guarda el detalle y un resumen
en `tests/data/resultados/evaluacion_<proveedor>_<modelo>_<prompt>.{json,md}` (carpeta ignorada por git):

```bash
LLM_PROVIDER=groq LLM_API_KEY=... python -m scripts.evaluar
LLM_PROVIDER=ollama LLM_BASE_URL=http://localhost:11434/v1 PROMPT_VERSION=lite python -m scripts.evaluar
```

Los requisitos de IA y las decisiones de diseño están en
[`docs/ia/requisitos-y-decisiones.md`](../docs/ia/requisitos-y-decisiones.md).

Criterio de aceptación: ≥ 80% de acierto en `categoria` y ≥ 90% en `requiereEscalamiento`.
Con Groq `qwen/qwen3.8-27b` y el prompt `full` da 100% y 100%. El mismo criterio corre como test
con `EVALUAR_RIA01=1 pytest tests/test_evaluacion.py`. Si el proveedor responde 429 (límite de
pedidos por minuto del plan gratuito), el evaluador espera y reintenta.

## Configuración

| Variable | Default | Uso |
|---|---|---|
| `MONGO_URI` | — | Obligatoria. `mongodb://classification_user:<pass>@mongo:27017/classification_db` |
| `LLM_PROVIDER` | `mock` | `mock`, `ollama` o `groq` |
| `LLM_API_KEY` | — | Solo para `groq`. Va en el `.env` de cada uno, nunca en el repo |
| `LLM_MODEL` | según el proveedor | Pisa el modelo por defecto |
| `LLM_BASE_URL` | según el proveedor | Pisa la URL por defecto |
| `LLM_TIMEOUT_MS` | `5000` | Timeout de cada llamada al LLM |
| `PROMPT_VERSION` | `full` | Lee `prompts/clasificacion_{PROMPT_VERSION}.txt` |
| `RABBITMQ_URL` | — | `amqp://<user>:<pass>@rabbitmq:5672/`. Sin esta variable no se consume la cola |
| `COLA_PREFETCH` | `10` | Cuántas solicitudes de la cola se procesan a la vez |
| `LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARNING` o `ERROR` |

## Estructura

```
app/
  main.py, config.py
  domain/           enums.py, models.py (Clasificacion y sus invariantes)
  application/
    ports/          llm_provider.py (LlmProvider), clasificacion_repository.py (ClasificacionRepository),
                    publicador_clasificaciones.py (PublicadorDeClasificaciones):
                    interfaces que implementa infrastructure
    clasificacion_facade.py  caso de uso de clasificar (Facade)
    procesador_de_solicitudes.py  caso de uso de atender una solicitud de la cola
    masking.py      enmascarado de datos personales
    errors.py       errores al consultar el LLM
  infrastructure/
    api/
      routes/       clasificaciones.py, health.py: endpoints
      schemas/      base.py, clasificacion.py, error.py, health.py: DTOs de request y response
      errors.py     formato común de error
      correlation.py  middleware X-Correlation-Id
      openapi.py    ajustes del OpenAPI
    llm/            mock_provider.py, openai_compatible_provider.py (Strategy),
                    factory.py (LlmProviderFactory), response_parser.py (Anti-Corruption Layer)
    persistence/    clasificacion_repository.py (Repository sobre MongoDB)
    messaging/      conexion.py, consumidor.py y publicador.py (RabbitMQ), manejador.py (valida cada
                    mensaje y decide si se confirma, se descarta o se reintenta), mensajes.py (DTOs
                    de los mensajes), topologia.py (exchanges, colas y bindings)
    prompts.py      lectura de las plantillas del prompt
    logs.py         logs JSON con correlationId
prompts/            clasificacion_full.txt, clasificacion_lite.txt
scripts/            exportar_openapi.py, evaluar.py (evaluación RIA01)
tests/              fakes.py (dobles de los puertos), test_*.py, data/tickets_eval.json (set de evaluación)
```

Arquitectura hexagonal (Ports and Adapters): las dependencias apuntan hacia adentro, `infrastructure → application → domain`.
`application/ports/` define las interfaces que implementan los adapters de `infrastructure`.

## Patrones aplicados

La arquitectura del servicio (Ports and Adapters) está en [Estructura](#estructura).

### Patrones de diseño (GoF)

| Patrón | Dónde | Qué problema resuelve |
|---|---|---|
| **Facade** | `ClasificacionFacade` (`application/clasificacion_facade.py`) | Clasificar implica enmascarar, armar el prompt, consultar al LLM con timeout, reintentar, validar y guardar. El endpoint llama a un solo método, `clasificar(ticket_id, titulo, descripcion)`, sin conocer esas piezas. |
| **Strategy** | Puerto `LlmProvider` (`application/ports/llm_provider.py`), con `MockLlmProvider` y `OpenAICompatibleProvider` (`infrastructure/llm/`) | Cambiar de LLM (mock, Ollama local, Groq) sin tocar el caso de uso: la facade solo conoce la interfaz. Hay una estrategia por contrato de API, así que un proveedor compatible con OpenAI se suma con configuración. |
| **Factory Method** | `LlmProviderFactory`: función `crear_llm_provider` (`infrastructure/llm/factory.py`) | Elegir y construir la estrategia según `LLM_PROVIDER`, con la URL, el modelo y el precalentamiento que corresponden a cada proveedor, y fallar al arrancar si falta la API key. |
| **Adapter** | `OpenAICompatibleProvider` (`infrastructure/llm/openai_compatible_provider.py`) y `RabbitMqPublicador` (`infrastructure/messaging/publicador.py`) | Traducir el puerto `LlmProvider` a la API HTTP `/chat/completions`, y sus errores (conexión, HTTP, formato) a los errores de la aplicación. El publicador traduce el puerto `PublicadorDeClasificaciones` a un mensaje AMQP. |

### Patrones de DDD

| Patrón | Dónde | Qué problema resuelve |
|---|---|---|
| **Repository** | Puerto `ClasificacionRepository` (`application/ports/clasificacion_repository.py`) y `MongoClasificacionRepository` (`infrastructure/persistence/`) | La aplicación guarda y lista clasificaciones sin depender de MongoDB; en los tests se usa un repositorio en memoria. |
| **Value Object** | `Clasificacion` (`domain/models.py`) | Inmutable y con sus invariantes validadas al construirse (enums cerrados, confianza entre 0 y 1, justificación de hasta 300 caracteres): no puede existir una clasificación inválida. |
| **Anti-Corruption Layer** | `parsear_clasificacion` (`infrastructure/llm/response_parser.py`) | El LLM devuelve texto libre que puede no cumplir el formato. El parser extrae el JSON y lo convierte en la `Clasificacion` del dominio; si no valida, la respuesta se rechaza y nunca llega al modelo de dominio. |

### Patrones de mensajería

| Patrón | Dónde | Qué problema resuelve |
|---|---|---|
| **Consumidor idempotente** | `ProcesadorDeSolicitudes` (`application/procesador_de_solicitudes.py`) e índice único en `solicitudId` | La cola entrega "al menos una vez". Una solicitud repetida no vuelve a consultar al LLM ni duplica el registro. |
| **Cola de mensajes muertos** | `Topologia` (`infrastructure/messaging/topologia.py`) | Una solicitud que no se pudo procesar tras los reintentos no se pierde ni bloquea la cola: queda apartada para revisarla. |
