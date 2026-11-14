# Componente de IA: requisitos, decisiones y validación

El componente de IA es classification-service: clasifica el texto libre de un ticket con un LLM
y devuelve categoría, urgencia, impacto, módulo afectado, si requiere escalamiento, confianza y
justificación. Se puede invocar por REST (`POST /api/clasificaciones`) o de forma asincrónica
por RabbitMQ ([`contracts/messaging/`](../../contracts/messaging/README.md)).

## Requisitos de IA

| ID | Requisito | Criterio de aceptación | Resultado medido | Cumple |
|---|---|---|---|---|
| RIA01 | Exactitud mínima | Sobre 20 tickets etiquetados: al menos 80% de acierto en categoría y 90% en la detección de los casos que deben escalarse | 100% en categoría y 100% en escalamiento | Sí |
| RIA02 | Latencia | El LLM responde en menos de 5 s; si no, la clasificación se da por fallida | Mediana 0,6 s, máxima 1,0 s; 0 de 20 respuestas superan los 5 s | Sí |
| RIA03 | Privacidad de datos | Al proveedor se envían solo el título y la descripción, con emails, teléfonos y DNI enmascarados. Lo que se guarda también está enmascarado | Tests automáticos del enmascarado y de lo que se envía y se guarda | Sí |
| RIA04 | Salida controlada | La respuesta tiene que ser un JSON con valores dentro de las listas cerradas; si no, se reintenta una vez y después se rechaza | Tests automáticos del parser y de la facade | Sí |
| RIA05 | Trazabilidad | Cada clasificación se guarda con proveedor, modelo, versión del prompt, latencia y justificación | Campos del documento en MongoDB y de la respuesta | Sí |

Los resultados de RIA01 y RIA02 son de la evaluación del 14/11/2026 con Groq
`qwen/qwen3.8-27b` y el prompt `full`.

La disponibilidad ya está cubierta por el requisito no funcional RNF01: si la IA no responde,
el ticket se crea igual y queda en `PENDIENTE_CLASIFICACION`. Por la cola, el servicio responde
`ClasificacionGenerada` con resultado `ERROR` y el ticket se puede reclasificar. Si lo que falla es
la base o el broker, la solicitud se reintenta 5 veces y después pasa a la cola de mensajes
muertos, así que tampoco se pierde.

### Exactitud (RIA01)

`classification-service/tests/data/tickets_eval.json` tiene 20 tickets con la clasificación
esperada. `python -m scripts.evaluar` los clasifica por el mismo camino que en producción.

| Campo | Acierto |
|---|---|
| categoria | 100% |
| urgencia | 90% |
| impacto | 90% |
| moduloAfectado | 100% |
| requiereEscalamiento | 100% |
| prioridad (calculada con la matriz) | 95% |

- Las 4 diferencias son de un nivel de urgencia o de impacto, y solo una cambia la prioridad:
  "Olvidé mi contraseña" sale P3 en vez de P4.
- Ningún caso crítico quedó sin escalar y ningún caso común se escaló de más.
- El criterio también corre como test: `EVALUAR_RIA01=1 pytest tests/test_evaluacion.py`.

La comparación entre proveedores, modelos y prompts está en la sección 12.3 de
`CONTEXTO_PROYECTO.md`. El modelo local `qwen2.5:1.5b` no llega al 90% de escalamiento.

### Latencia (RIA02)

- Cada llamada al LLM tiene un timeout de 5 s (`LLM_TIMEOUT_MS`), dentro de un presupuesto total
  de 6 s que incluye el reintento.
- Por REST, superar el tiempo responde 504 `LLM_TIMEOUT`.
- Por cola, se responde `ClasificacionGenerada` con resultado `ERROR` y código `LLM_TIMEOUT`.

### Privacidad (RIA03)

- Antes de consultar al LLM se reemplazan los emails por `[EMAIL]`, los teléfonos por
  `[TELEFONO]` y los DNI por `[DNI]`.
- En MongoDB se guardan el texto ya enmascarado y la respuesta cruda del LLM, también
  enmascarada. El texto original no se guarda ni se escribe en los logs.
- Los eventos de dominio no llevan la descripción del ticket. Solo viaja en
  `ClasificacionSolicitada`, por una cola que consume únicamente classification-service.
- Con `LLM_PROVIDER=ollama` el modelo corre en la máquina y no sale nada a internet.
- La API key del proveedor llega por variable de entorno y no aparece en el repositorio ni en
  los logs.

## Decisiones de diseño de IA

| Tema | Decisión | Motivo |
|---|---|---|
| Qué hace la IA | Interpreta el texto y sugiere una clasificación. No devuelve la prioridad | La prioridad y el escalamiento son reglas de negocio auditables; un error del modelo no puede fijar una prioridad por sí solo |
| Modelo | Un LLM por API, sin entrenar un modelo propio | No hay un dataset etiquetado de tickets reales; un LLM con instrucciones y ejemplos alcanza la exactitud pedida |
| Proveedores | `mock` (reglas por palabras clave), `ollama` (modelo local) y `groq` (API en internet), elegidos con `LLM_PROVIDER` | El mock permite desarrollar, correr los tests y hacer la demo sin internet; cambiar de proveedor no toca el caso de uso |
| Un adapter por contrato de API | Ollama y Groq comparten `OpenAICompatibleProvider` | Los dos hablan el contrato de OpenAI; sumar otro proveedor compatible es solo configuración |
| Prompt | Plantillas versionadas en archivos: `full` (default) y `lite` (para modelos locales chicos) | El prompt cambia sin tocar código, y cada clasificación guarda con qué versión se hizo |
| Salida | Se pide JSON y se valida contra el dominio antes de usarla | El LLM puede devolver texto que no cumple el formato; lo inválido nunca llega al modelo de dominio |
| Temperatura | 0 | Respuestas lo más repetibles posible para una tarea de clasificación |
| Baja confianza | Si la confianza es menor a 0,6, el ticket se marca para revisión manual | Un agente revisa lo que el modelo no pudo clasificar con seguridad |
| Invocación | Por REST y por cola, con la misma `ClasificacionFacade` | La cola desacopla la creación del ticket del tiempo de respuesta del LLM; el REST queda para reclasificar |
| Solicitudes repetidas | Una solicitud ya procesada no vuelve a consultar al LLM | La cola entrega "al menos una vez"; repetir la consulta gasta cuota y podría dar otra respuesta |
| Falla del proveedor | Respuesta con resultado `ERROR` en lugar de reintentar por la cola; el ticket queda pendiente y se puede reclasificar | El LLM es un servicio externo que puede fallar o limitar pedidos |

## Validación del componente

| Prueba | Qué valida | Cómo se corre |
|---|---|---|
| Tests unitarios | Enmascarado, parser, facade, mock, mensajes contra el contrato, idempotencia | `pytest` en `classification-service/` |
| Integración con MongoDB | Guardado, paginado e índice único por solicitud | `MONGO_URI_TEST=... pytest tests/test_mongo_repository.py` |
| Integración con RabbitMQ | Consumo, respuesta, error del LLM, solicitud repetida, reintentos y cola de mensajes muertos | `RABBITMQ_URL_TEST=... pytest tests/test_rabbitmq_worker.py` |
| Evaluación de 20 casos | Exactitud y latencia con el proveedor real (RIA01 y RIA02) | `python -m scripts.evaluar` |
