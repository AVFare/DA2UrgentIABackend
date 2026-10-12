# ADR-06: Clasificación por REST sincrónico, con timeout y fallback

**Estado:** aceptada (para la Primera Parte)

## Contexto

La Primera Parte no exige mensajería, y el ticket tiene que tener prioridad apenas se crea. El LLM es un servicio externo que puede fallar o tardar.

## Decisión

- `ticket-service` llama a `POST /api/clasificaciones` por REST, con un timeout de 7 s (`CLASSIFICATION_TIMEOUT_MS`).
- `classification-service` le da 5 s al LLM y reintenta una vez si la respuesta no es válida y queda tiempo.
- Si la clasificación falla, el ticket se crea igual (201) en `PENDIENTE_CLASIFICACION`. Un agente lo reclasifica con `POST /api/tickets/{id}/reclasificacion`, que responde 503 `IA_NO_DISPONIBLE` si la IA sigue caída.

## Consecuencias

- El solicitante recibe la prioridad en la misma respuesta.
- La creación de un ticket puede tardar hasta 7 s, porque queda acoplada en el tiempo a la IA.
- La creación nunca falla por culpa de la IA.
- En la parte 2, la clasificación pasa a la cola de mensajes.
