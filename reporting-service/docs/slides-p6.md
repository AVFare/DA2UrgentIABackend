# TriageDesk · defensa

Borrador del guion propuesto. Los bloques de P1–P5 requieren contenido y revisión
de sus responsables; solo se verificó la implementación de P6 en esta rama.

## 1. Apertura · P6

- Mesa de ayuda con clasificación asistida por IA.
- La IA sugiere; el dominio decide prioridad y SLA.
- Casos críticos: escalamiento, notificación y observabilidad.
- Francisco Nappa, Ana Fare, Florencia Quiñonez, Juan Lopez Korach,
  Santiago Gil Ishikawa y Gustavo Forcherio.

## 2. Arquitectura · P6

- Seis servicios; gateway como entrada pública.
- Java para core, usuarios y gateway; Python para IA; NestJS para soporte.
- Base independiente por servicio; ninguna lectura de tablas ajenas.
- Reportes recibe eventos HTTP y mantiene su propia vista MongoDB.
- Diagrama fuente: docs/diagrams/componentes-reporting.puml.

## 3. DDD estratégico · P3 (por revisar)

- Core: gestión de tickets, prioridad, SLA y estados.
- Contextos de soporte: usuarios, clasificación, notificaciones y reportes.
- Contratos explícitos entre contextos y lenguaje ubicuo compartido.
- Completar con el mapa de contextos del responsable.

## 4. Dominio y hexagonal · P2 (por revisar)

- Matriz urgencia por impacto; prioridad P1–P4.
- Invariantes y transiciones pertenecen al dominio de tickets.
- Puertos para repositorio, clasificador y publicación de eventos.
- Completar con diagramas de clases, estados y capas de P2.

## 5. Clasificación asistida · P4 (por revisar)

- Categoría, urgencia, impacto y módulo; la IA no devuelve prioridad.
- Enmascarado de datos personales, validación, timeout y fallback.
- Proveedor mock para desarrollo y contingencia.
- Completar con proveedor real y resultados de evaluación de P4.

## 6. Eventos y notificaciones · P5 (por revisar)

- Sobre estándar: eventId, eventType, version, occurredAt y correlationId.
- Snapshot del ticket sin descripcion.
- Entrega con posibles duplicados y desorden; consumidores idempotentes.
- Completar con BPMN, secuencia y evidencias de notificaciones de P5.

## 7. Reportes y SLA · P6

- CQRS: ticket_view mantiene el lado de lectura.
- Seis estrategias; upsert atómico y fechas monotónicas.
- Inbox persistente: PENDIENTE, proyección, PROCESADO.
- Resumen, consultas paginadas y cumplimiento de SLA por prioridad.
- Un duplicado no produce efectos; un fallo puede reintentarse.

## 8. Demo integrada · P1 (pendiente de integración)

- Crear un ticket crítico por Swagger del gateway.
- Mostrar prioridad P1, escalamiento, notificación y resumen.
- Mostrar fallback: IA caída y posterior reclasificación.
- Completar con evidencias y video del flujo real del equipo.

## 9. Validación y cierre · P6

- 22 tests unitarios y 39 de integración HTTP con MongoDB real.
- Concurrencia, reintentos, orden de eventos, reaperturas y límites de SLA.
- Demo del JavaScript compilado con respuestas reales guardadas.
- Docker preparado; build pendiente en este WSL.
- Pendientes: revisión del equipo, integración completa y ensayos.
