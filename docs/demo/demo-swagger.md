# Demo desde Swagger

El mismo flujo de `demo.http`, hecho a mano desde el Swagger unificado del gateway.

Con todo levantado (`docker compose up -d --build --wait`), abrir
<http://localhost:8080/swagger-ui.html>. El selector **Select a definition**, arriba a la
derecha, cambia de servicio.

Para usar un token: botón **Authorize**, pegar el `accessToken` sin la palabra `Bearer` y
confirmar. Al cambiar de servicio en el selector puede hacer falta autorizar de nuevo.

## Flujo principal

La clasificación es asincrónica: el alta responde enseguida en `PENDIENTE_CLASIFICACION` y la
prioridad, el SLA y el escalamiento llegan por RabbitMQ cuando responde classification-service.
Por eso el paso 4 consulta el ticket hasta verlo clasificado.

| Paso | Servicio | Operación | Qué cargar | Resultado esperado |
|---|---|---|---|---|
| 1 | user-service | `POST /api/auth/login` | `solicitante@urgentia.local` / `Usuario123!` | 200. Copiar `accessToken` |
| 2 | ticket-service | `POST /api/tickets` | Authorize con el token del solicitante. Body de abajo | 201, `estado: PENDIENTE_CLASIFICACION`, `prioridad: null`. Copiar `id` |
| 3 | user-service | `POST /api/auth/login` | `guardia@urgentia.local` / `Agente123!` | 200. Copiar `accessToken` |
| 4 | ticket-service | `GET /api/tickets/{id}` | Authorize con el token del agente. `id` del paso 2 | `prioridad: P1`, `estado: ESCALADO`, `clasificacion.proveedor: mock`. Si sigue en `PENDIENTE_CLASIFICACION`, ejecutar de nuevo |
| 5 | notification-service | `GET /api/notificaciones` | Token del agente. `ticketId` = id del paso 2 | Notificación `TicketEscalado` a `GRUPO:GUARDIA` (si la lista viene vacía, ejecutar de nuevo) |
| 6 | reporting-service | `GET /api/reportes/resumen` | Authorize con el token del agente | `escalados` y `porPrioridad.P1` en 1 o más |
| 7 | reporting-service | `GET /api/reportes/sla` | Mismo token | 200 |

Body del paso 2:

```json
{
  "titulo": "No puede ingresar nadie",
  "descripcion": "Producción caída, todos los usuarios bloqueados en el login desde las 9"
}
```

El flujo espera `LLM_PROVIDER=mock` y `CLASIFICACION_MODO=async`, que son los valores de
`.env.example`.

## Controles del gateway

| Paso | Servicio | Operación | Cómo | Resultado esperado |
|---|---|---|---|---|
| 8 | ticket-service | `GET /api/tickets` | Sin token: **Authorize** → **Logout** | 401 `NO_AUTENTICADO` |
| 9 | reporting-service | `GET /api/reportes/resumen` | Authorize con el token del solicitante | 403 `SIN_PERMISO` |

En cualquier respuesta, el header `x-correlation-id` muestra el identificador que el
gateway generó para ese pedido.
