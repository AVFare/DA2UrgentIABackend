# P6 · cobertura del Trello

Fuente: exportación local `aKNGq6oe - desarrolloapps2.json`. Este documento no
modifica el tablero ni marca actividades humanas como realizadas.

| Tarjeta                                                           | Entregable de esta rama                                                               | Estado / coordinación pendiente                                                                                                                            |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [33 · Proyecto base + contrato](https://trello.com/c/BHohsbka)    | NestJS, Swagger, Mongoose, class-validator y `contracts/reporting-service.yaml`       | Implementado. La propuesta `eventos-consumidos.schema.json` debe revisarse con P2/P5 antes de incorporarla al esquema compartido                           |
| [34 · Proyecciones](https://trello.com/c/W2AkSDS5)                | POST de eventos, idempotencia, seis estrategias, upsert y control de eventos antiguos | Implementado y probado con concurrencia y fallos                                                                                                           |
| [35 · Consultas](https://trello.com/c/HHWQrMuj)                   | Resumen, SLA completo y tickets con filtros/paginación                                | Implementado y probado                                                                                                                                     |
| [36 · Tests + health + Dockerfile](https://trello.com/c/FvcYqijX) | Jest, integración con MongoDB 7, health, Dockerfile y Compose propio                  | Código y pruebas locales verificados. Build del contenedor pendiente: Docker no está integrado con esta terminal WSL                                       |
| [37 · Coordinación](https://trello.com/c/Wny9EkgQ)                | Informe P6, nombres, evidencias, PDF, slides y guion con tiempos                      | Borradores preparados. Faltan revisión de cada integrante, informe global, capturas del flujo completo y ensayos reales                                    |
| [38 · Docs](https://trello.com/c/Z1ocOiVk)                        | Diagrama de componentes, README de patrones y borrador de secciones 1, 2, 6, 13 y 16  | Material P6 preparado. No había un informe compartido para revisar; los títulos se basan en el documento de contexto y deben alinearse con el índice final |

## Acuerdos para P2 y P5

La propuesta local conserva el sobre, los seis nombres de eventos y los campos
del snapshot del contexto. No reemplaza `contracts/events/ticket-events.schema.json`.
La validación exige UUID v4, fechas UTC y campos nullable presentes explícitamente.

Confirmar antes de integrar:

- Que los productores emitan el snapshot completo y `estadoAnterior`/`motivo`
  en los eventos que corresponden.
- El orden relativo de eventos que tengan el mismo `occurredAt`: sin secuencia,
  P6 solo puede aplicar el orden de llegada para esos empates.
- Que el snapshot incluya en una futura revisión acordada la fecha real de
  resolución. Actualmente un `CERRADO` recibido antes que su resolución puede
  dejar esa fecha desconocida; P6 no la inventa ni sobrescribe snapshots con
  eventos viejos. Esto se documenta como límite del contrato vigente.
- Si `escalado` debe representar el estado actual o un historial. Esta entrega
  lo interpreta como estado actual `ESCALADO`, coherente con el resumen de ejemplo.

## Integración a cargo de P1/P2

P1: agregar el contenedor de reportes al Compose compartido y las rutas de
consulta/documentación del gateway. Conservar `/api/eventos` como ruta interna.
Usar la red que existe en el repositorio: `urgentia-net`.

P2: incluir `http://reporting-service:8085/api/eventos` en `EVENT_SUBSCRIBERS`,
mantener la correlación y reintentar respuestas 500. La recuperación del inbox
pendiente depende de esos reintentos; no es un worker autónomo.

## Pendientes humanos

- Confirmar la asignación P1–P5 entre los integrantes (Francisco tiene P6).
- Integrar este borrador con los textos y diagramas de los demás integrantes.
- Solicitar y registrar sus revisiones; aprobar PRs por el flujo del equipo.
- Capturar el flujo completo desde el gateway y evaluar al proveedor LLM real.
- Ejecutar y cronometrar los ensayos. No se enviaron mensajes al equipo.
- No se hicieron push, PR ni cambios remotos desde esta tarea.
