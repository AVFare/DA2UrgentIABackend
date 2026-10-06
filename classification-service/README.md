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

Los tests del repositorio corren contra un MongoDB real solo si se define `MONGO_URI_TEST`:

```bash
MONGO_URI_TEST="mongodb://root:mongo@localhost:27017/?authSource=admin" pytest tests/test_mongo_repository.py
```

## Cómo correrlo con Docker

Desde la raíz del repo, con el `.env` creado a partir de `.env.example`:

```bash
docker compose up -d --build --wait classification-service
docker compose logs classification-service
```

Levanta también MongoDB, del que depende. Dentro de la red del Compose el servicio responde en
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
| `LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARNING` o `ERROR` |

## Estructura

```
app/
  main.py, config.py
  domain/           enums.py, models.py (Clasificacion y sus invariantes)
  application/
    ports/          llm_provider.py (LlmProvider), clasificacion_repository.py (ClasificacionRepository):
                    interfaces que implementa infrastructure
    clasificacion_facade.py  caso de uso de clasificar (Facade)
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
    prompts.py      lectura de las plantillas del prompt
    logs.py         logs JSON con correlationId
prompts/            clasificacion_full.txt, clasificacion_lite.txt
scripts/            exportar_openapi.py, evaluar.py (evaluación RIA01)
tests/              fakes.py (dobles de los puertos), test_*.py, data/tickets_eval.json (set de evaluación)
```

Las dependencias apuntan hacia adentro: `infrastructure → application → domain`.
`application/ports/` define las interfaces que implementan los adapters de `infrastructure`.
