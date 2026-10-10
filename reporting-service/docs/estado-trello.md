# P6 · cobertura del Trello

Fuente: exportación local `aKNGq6oe - desarrolloapps2.json` y revisión de
`dev/entrega-1` del 10 de octubre de 2026. El export permanece fuera del control
de versiones. Este documento no modifica el tablero ni marca actividades humanas
como realizadas.

| Tarjeta                                                           | Entregable de esta rama                                                                                   | Estado / coordinación pendiente                                                                          |
| ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| [33 · Proyecto base + contrato](https://trello.com/c/BHohsbka)    | NestJS, Swagger con JWT para el gateway, Mongoose, validación y OpenAPI                                   | Implementado. Correlación libre alineada con el esquema compartido                                       |
| [34 · Proyecciones](https://trello.com/c/W2AkSDS5)                | Inbox, idempotencia, seis estrategias, upsert y control de eventos antiguos                               | Implementado y probado con concurrencia y fallos                                                         |
| [35 · Consultas](https://trello.com/c/HHWQrMuj)                   | Resumen, SLA y tickets con filtros/paginación                                                             | Implementado y probado                                                                                   |
| [36 · Tests + health + Dockerfile](https://trello.com/c/FvcYqijX) | Jest, MongoDB 7 real, health, Dockerfile, Compose local y compartido, CI con test:e2e y flujo por gateway | Pruebas locales verificadas. Build y ejecución del Compose completo pendientes del CI del PR             |
| [37 · Coordinación](https://trello.com/c/Wny9EkgQ)                | Informe P6, nombres, evidencias, PDF, slides y guion con tiempos                                          | Borradores preparados. Faltan revisiones, informe global, capturas del flujo completo y ensayos          |
| [38 · Docs](https://trello.com/c/Z1ocOiVk)                        | Diagrama de componentes, README de patrones y secciones 1, 2, 6, 13 y 16                                  | Material actualizado a UrgentIA y a las tecnologías implementadas. Alinear con el índice académico final |

## Compatibilidad con P2 y P5

`contracts/events/ticket-events.schema.json` es el contrato compartido. P6 no lo
reemplaza; `eventos-consumidos.schema.json` documenta su validación local. Los
identificadores son UUID v4 y las fechas UTC; `correlationId` acepta cualquier
string no vacío. Los campos nullable deben estar presentes explícitamente.

P2 ya publica el snapshot completo sin descripción, con `estadoAnterior` y
`motivo` cuando corresponden. Ya incluye a P6 en `EVENT_SUBSCRIBERS`, conserva
la correlación y reintenta errores 500. La recuperación del inbox de P6 depende
de esos reintentos; no es un worker autónomo.

Pendientes para coordinar:

- P2: cuando falla la IA, publicar el snapshot actualizado de
  `PENDIENTE_CLASIFICACION`. Actualmente el agregado cambia pero solo emite
  `TicketCreado` con estado `NUEVO`, por lo que la vista de P6 queda desactualizada.
- P5: recuperar fallos entre el registro del evento, el canal y la persistencia
  de la notificación. Un evento pendiente no debe responder como duplicado
  completado; probar interrupciones antes y después de guardar la notificación.
- P5: declarar bearer/JWT en Swagger para las consultas protegidas por el gateway.
- P2/P5: confirmar el orden de eventos con igual `occurredAt` y considerar en una
  futura revisión una secuencia y `fechaResolucion` en el snapshot. Si el cierre
  llega antes que la resolución y esta es antigua, su fecha queda desconocida;
  P6 no la inventa ni sobrescribe vistas más recientes.
- Confirmar que `escalado` representa el estado actual `ESCALADO`, como en el
  resumen acordado, y no un historial.

## Integración con P1

Las rutas de consultas y documentación de P6 ya existen en el gateway. Esta rama
agrega el contenedor al Compose raíz con su base, healthcheck y red `urgentia-net`;
no publica su puerto ni `/api/eventos` al exterior.

Revisar los cambios compartidos de Compose y CI, ejecutar el PR completo y capturar
el flujo de login, ticket crítico, notificación y reportes. El verificador usa IA
mock; el caso de IA caída queda pendiente del ajuste de P2 y de su prueba integrada.

## Pendientes humanos

- Confirmar la asignación P1–P5 entre los integrantes (Francisco tiene P6).
- Integrar estos borradores con los textos y diagramas del resto del equipo.
- Solicitar y registrar las revisiones; usar PR a `dev/entrega-1` con merge commit.
- P4: revisar el PR documental #34 y adjuntar evidencia del proveedor LLM real.
- P3: comprobar el login semilla y los roles en la demo integrada.
- Capturar el flujo por gateway y ejecutar ensayos cronometrados.
- No se enviaron mensajes, no se hizo push y no se publicó un PR desde esta tarea.
