# classification-service

Clasifica el texto de un ticket con un LLM: categoría, urgencia, impacto, módulo afectado,
si requiere escalamiento, confianza y justificación. La IA sugiere y el dominio decide:
este servicio nunca devuelve la prioridad (la calcula ticket-service).

| | |
|---|---|
| Puerto | 8082 |
| Tecnología | Python 3.12, FastAPI, Pydantic |
| Base de datos | MongoDB `classification_db`, colección `clasificaciones` |
| Contrato | [`contracts/classification-service.yaml`](../contracts/classification-service.yaml) |
| Swagger propio | http://localhost:8082/docs (OpenAPI en `/openapi.json`) |
| Dueño | P4 |

## Cómo correrlo sin Docker

Desde esta carpeta, con Python 3.12:

```bash
python -m venv .venv
.venv/Scripts/activate            # Windows (Git Bash). En Linux/macOS: source .venv/bin/activate
pip install -r requirements-dev.txt
pytest
python -m app.main
```

Por defecto usa `LLM_PROVIDER=mock`, que no necesita internet ni API key.

## Contrato

El contrato se genera desde el código. Si cambia la API, se vuelve a exportar y se commitea
junto con el cambio:

```bash
python -m scripts.exportar_openapi
```

Cambiar el contrato requiere avisar en el grupo y la aprobación de P2 (ticket-service lo consume).

## Configuración

| Variable | Default | Uso |
|---|---|---|
| `MONGO_URI` | — | `mongodb://classification_user:<pass>@mongo:27017/classification_db` |
| `LLM_PROVIDER` | `mock` | `mock` (reglas por palabras clave, sin internet), `ollama` (modelo local) o `groq` (API en internet) |
| `LLM_API_KEY` | — | Solo para `groq`. Va en el `.env` de cada uno, nunca en el repo |
| `LLM_MODEL` | — | Modelo del proveedor |
| `LLM_BASE_URL` | según el proveedor | Pisa la URL por defecto (por ejemplo, otro host de Ollama) |
| `LLM_TIMEOUT_MS` | `5000` | Timeout de cada llamada al LLM |
| `PROMPT_VERSION` | `v1` | Lee `prompts/clasificacion_{PROMPT_VERSION}.txt` |
| `LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARNING` o `ERROR` |

## Estructura

```
app/
  main.py, config.py
  domain/           enums.py, models.py (Clasificacion y sus invariantes)
  application/      ports.py (LlmProvider, ClasificacionRepository), clasificacion_facade.py, masking.py
  infrastructure/
    api/            routes.py, schemas.py, errors.py, health.py, correlation.py, openapi.py
    llm/            mock_provider.py, openai_compatible_provider.py, factory.py, response_parser.py
    persistence/    clasificacion_repository.py
    logs.py         logs JSON con correlationId
prompts/            clasificacion_v1.txt
scripts/            exportar_openapi.py
tests/
```

Las dependencias apuntan hacia adentro: `infrastructure → application → domain`. Las
interfaces que necesita la aplicación (`ports.py`) viven en `application`, no en
`infrastructure`.
