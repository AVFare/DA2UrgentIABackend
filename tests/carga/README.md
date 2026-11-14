# Prueba de carga básica

Mide cómo responde el sistema bajo carga con [k6](https://k6.io). Se corre con
`LLM_PROVIDER=mock`, para no depender del proveedor de IA ni gastar su cuota.

| Modo | Qué hace | Qué mide |
|---|---|---|
| `gateway` | Crea tickets por el api-gateway (`POST /api/tickets`), como un solicitante | Tiempo de respuesta y errores de punta a punta |
| `cola` | Publica `ClasificacionSolicitada` en RabbitMQ | Cuánto se acumula la cola de clasificación y cuánto tarda en vaciarse |

En los dos modos se muestrea una vez por segundo el tamaño de `clasificacion.solicitudes` y de
su cola de mensajes muertos.

## Cómo correrla

Con todo levantado (`docker compose up -d --build --wait`) y desde la raíz del repo. No hace
falta instalar k6: corre en un contenedor, dentro de la red del Compose.

```bash
docker run --rm -i --network da2urgentiabackend_urgentia-net \
  -e MODO=gateway -e TASA=20 -e DURACION_S=60 \
  grafana/k6:1.3.0 run - < tests/carga/carga.js
```

| Variable | Default | Uso |
|---|---|---|
| `MODO` | `gateway` | `gateway` o `cola` |
| `TASA` | `20` | Pedidos por segundo que se sostienen |
| `DURACION_S` | `60` | Segundos a esa tasa. Antes sube durante 15 s y al final baja durante 5 s |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | los de `.env.example` | Usuario de RabbitMQ, para leer el tamaño de las colas |

En Git Bash de Windows hay que anteponer `MSYS_NO_PATHCONV=1` al comando.

La prueba falla si el 95% de las creaciones de ticket tarda más de 8 s (RNF02) o si falla más
del 1% de los pedidos.

## Resultados

Corridas del 14/11/2026 en una notebook, con los 9 contenedores del Compose en la misma máquina y
`LLM_PROVIDER=mock`. Cada corrida sostiene la tasa durante 60 s.

### Por el gateway

| Tasa pedida | Tickets creados | Errores | Mediana | p90 | p95 | Máximo |
|---|---|---|---|---|---|---|
| 20 por segundo | 1407 | 0% | 26 ms | 37 ms | 41 ms | 1,0 s |
| 100 por segundo | 6924 | 0% | 46 ms | 953 ms | 1,2 s | 5,4 s |

- Todos los tickets quedaron clasificados; ninguno en `PENDIENTE_CLASIFICACION`.
- A 100 por segundo el sistema llega a su límite en esta máquina: k6 no pudo iniciar 83 pedidos
  (1%) y sostuvo 86 por segundo. Aun así el p95 queda muy por debajo de los 8 s de RNF02.

### Por la cola

| Tasa pedida | Solicitudes | Respuestas | Mensajes muertos | Cola: mediana | Cola: máximo | Vaciado al terminar |
|---|---|---|---|---|---|---|
| 20 por segundo | 1407 | 1407 | 0 | 1 | 2 | 5 s |
| 100 por segundo | 7007 | 7007 | 0 | 344 | 1102 | 19 s |

Con la topología de `contracts/messaging/` y `COLA_PREFETCH=10`.

- No se perdió ninguna solicitud: hay una `ClasificacionGenerada` por cada `ClasificacionSolicitada`.
- A 20 por segundo el worker procesa al ritmo de llegada y la cola no se acumula.
- A 100 por segundo el worker queda por detrás de la llegada: la cola llega a 1102 mensajes y se
  vacía sola en 19 s al terminar la carga. Con `COLA_PREFETCH=4` la misma carga acumula hasta
  3699 mensajes, por eso el default es 10.
- Cada respuesta se escribe en dos colas quorum: `tickets.clasificaciones` (la del contrato) y la
  cola propia de la prueba. En uso normal se escribe solo en la primera.
- Mientras nadie consume `tickets.clasificaciones`, las respuestas de la prueba quedan ahí. Hay
  que vaciarla a mano después de correrla (en la consola, *Purge Messages*).
- El tamaño de las colas se lee de la API de administración de RabbitMQ, que actualiza sus
  números cada 5 s: los valores son aproximados.

Con el proveedor real el límite lo pone el LLM, no el sistema: cada clasificación tarda unos
0,6 s (mediana con Groq) y el plan gratuito limita los pedidos por minuto.
