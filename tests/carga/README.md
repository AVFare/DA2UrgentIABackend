# Prueba de carga básica

Mide cómo responde el sistema bajo carga con [k6](https://k6.io). Se corre con
`LLM_PROVIDER=mock`, para no depender del proveedor de IA ni gastar su cuota.

| Modo | Qué hace | Qué mide |
|---|---|---|
| `gateway` | Crea tickets por el api-gateway (`POST /api/tickets`), como un solicitante | Tiempo de respuesta del alta, cuánto tarda en llegar la clasificación y si algún ticket queda sin clasificar |
| `cola` | Publica `ClasificacionSolicitada` en RabbitMQ | Cuánto se acumula la cola de clasificación y cuánto tarda en vaciarse |

En los dos modos se muestrea una vez por segundo el tamaño de `clasificacion.solicitudes`, de
`tickets.clasificaciones` y de la cola de mensajes muertos de clasificación.

En el modo `gateway`, uno de cada 10 tickets (`MUESTRA`) se consulta cada 0,2 s hasta que deja de
estar en `PENDIENTE_CLASIFICACION`, como hace el frontend, para medir cuánto tarda la
clasificación desde que se pidió el alta. Al terminar, la prueba espera a que se vacíen las colas
de clasificación y cuenta cuántos tickets quedaron en `PENDIENTE_CLASIFICACION`.

## Cómo correrla

Con todo levantado (`docker compose up -d --build --wait`) y desde la raíz del repo. No hace
falta instalar k6: corre en un contenedor, dentro de la red del Compose.

```bash
docker run --rm -i --network da2urgentiabackend_urgentia-net \
  -e MODO=gateway -e TASA=20 -e DURACION_S=60 \
  -e RABBITMQ_USER -e RABBITMQ_PASSWORD \
  grafana/k6:1.3.0 run - < tests/carga/carga.js
```

| Variable | Default | Uso |
|---|---|---|
| `MODO` | `gateway` | `gateway` o `cola` |
| `TASA` | `20` | Pedidos por segundo que se sostienen |
| `DURACION_S` | `60` | Segundos a esa tasa. Antes sube durante 15 s y al final baja durante 5 s |
| `MUESTRA` | `10` | En el modo `gateway`, se mide la clasificación de uno de cada `MUESTRA` tickets |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | los de `.env.example` | Usuario de RabbitMQ, para leer el tamaño de las colas. Con `-e` sin valor se toman del entorno (por ejemplo, después de `set -a; . ./.env; set +a`) |

En Git Bash de Windows hay que anteponer `MSYS_NO_PATHCONV=1` al comando.

La prueba falla si el 95% de las altas de ticket tarda más de 8 s (RNF02), si falla más del 1% de
los pedidos o si algún ticket de la muestra no se clasifica en 30 s.

Conviene empezar cada corrida con el sistema en reposo: las colas vacías y el outbox de
ticket-service sin mensajes pendientes. Si no, lo que quedó de la corrida anterior se mezcla con
la nueva.

## Resultados

Corridas del 16/11/2026 en una notebook, con los 9 contenedores del Compose en la misma máquina,
`LLM_PROVIDER=mock`, `CLASIFICACION_MODO=async` y `COLA_PREFETCH=10`. Cada corrida sostiene la
tasa durante 60 s.

### Por el gateway

Alta del ticket (`POST /api/tickets`, responde en `PENDIENTE_CLASIFICACION`):

| Tasa pedida | Tickets creados | Errores | Mediana | p95 | Máximo |
|---|---|---|---|---|---|
| 10 por segundo | 707 | 0% | 11 ms | 18 ms | 39 ms |
| 20 por segundo | 1407 | 0% | 14 ms | 25 ms | 108 ms |
| 100 por segundo | 6489 | 0% | 39 ms | 187 ms | 481 ms |

Tiempo desde el alta hasta que el ticket aparece clasificado:

| Tasa pedida | Mediana | p95 | Máximo | Sin clasificar a los 30 s (muestra) | Pendientes al vaciarse las colas |
|---|---|---|---|---|---|
| 10 por segundo | 0,8 s | 1,2 s | 1,3 s | 0 | 0 |
| 20 por segundo | 5,7 s | 9,6 s | 9,8 s | 0 | 0 |
| 100 por segundo | 16,8 s | 29,3 s | 29,9 s | 587 | 3801 |

- El alta responde rápido en las tres tasas, muy por debajo de los 8 s de RNF02: ya no espera a
  la IA.
- El límite está en el outbox de ticket-service, no en la clasificación. Publica los mensajes de a
  uno: por cada mensaje espera la confirmación del broker y lo marca como publicado en la base antes
  de pasar al siguiente. La confirmación es la que garantiza que no se pierda ninguno; lo que pone
  el límite es hacerlo en serie. Así llegó a unos 50 mensajes por segundo (mediana; máximo 95).
  Cada ticket genera entre 3 y 4 mensajes (`TicketCreado`, `ClasificacionSolicitada`,
  `TicketClasificado` y, si corresponde, `TicketEscalado`), así que alcanza para unos 15 tickets
  por segundo.
- A 10 por segundo todo se procesa al ritmo de llegada. A 20 por segundo el outbox se va
  atrasando durante la corrida, aunque la cola de clasificación nunca pasa de 4 mensajes. A 100
  por segundo el atraso llegó a 202 s: al vaciarse las colas quedaban 3801 tickets pendientes,
  porque sus mensajes todavía estaban en el outbox. No se perdió ninguno: después se clasificaron
  todos.
- A 100 por segundo k6 no pudo iniciar 518 altas (7%): sus 300 usuarios virtuales estaban
  ocupados consultando los tickets de la muestra, que tardaban en clasificarse.

### Por la cola

| Tasa pedida | Solicitudes | Clasificaciones | Mensajes muertos | Cola: mediana | Cola: máximo | Vaciado al terminar |
|---|---|---|---|---|---|---|
| 20 por segundo | 1407 | 1407 | 0 | 2 | 2 | 2 s |
| 100 por segundo | 7007 | 7007 | 0 | 1265 | 2912 | 50 s |

- No se perdió ninguna solicitud: hay una clasificación guardada por cada `ClasificacionSolicitada`
  (contadas en MongoDB) y ninguna fue a la cola de mensajes muertos.
- A 20 por segundo el worker procesa al ritmo de llegada y la cola no se acumula.
- A 100 por segundo el worker procesa unas 55 solicitudes por segundo: la cola llega a 2912
  mensajes y se vacía sola en 50 s al terminar la carga. Con `COLA_PREFETCH=4` acumula más, por
  eso el default es 10.
- Las respuestas van a `tickets.clasificaciones` y a una cola propia de la prueba, que se borra al
  terminar. ticket-service confirma sin efectos las de tickets que no existe, como las de esta
  prueba.
- El tamaño de las colas se lee de la API de administración de RabbitMQ, que actualiza sus
  números cada 5 s: los valores son aproximados.

Con el proveedor real el límite lo pone el LLM, no el sistema: cada clasificación tarda unos
0,6 s (mediana con Groq) y el plan gratuito limita los pedidos por minuto.
