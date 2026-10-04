# Defensa · guion y coordinación de P6

Borrador para revisar con el equipo. No había un guion compartido en el repositorio.
No se contactó a integrantes ni se realizaron ensayos desde esta tarea.

## Apertura de Francisco (P6 · aproximadamente 90 segundos)

“Nuestro proyecto es TriageDesk, una mesa de ayuda donde el usuario describe su
problema y la inteligencia artificial sugiere una clasificación. La prioridad y
el SLA se deciden con reglas del dominio, para conservar un comportamiento
predecible. La arquitectura separa gateway, tickets, usuarios, clasificación,
notificaciones y reportes. Cada servicio tiene su propia base de datos.

Mi parte es reporting-service. Recibe eventos de tickets, mantiene una vista de
lectura y permite consultar totales, tickets y SLA. No lee la base de tickets ni
recalcula su prioridad. La entrega puede incluir eventos duplicados o fuera de
orden: el servicio los registra por identificador y compara fechas de forma
atómica para conservar la información más reciente.”

## Agenda propuesta (12 minutos, por confirmar con la cátedra)

| Responsable           | Bloque                            | Tiempo objetivo |
| --------------------- | --------------------------------- | --------------- |
| P6 · Francisco        | Problema y arquitectura           | 2:00            |
| P3 · nombre pendiente | DDD estratégico y contextos       | 1:00            |
| P2 · nombre pendiente | Dominio, prioridad y hexagonal    | 2:00            |
| P4 · nombre pendiente | Clasificación e IA                | 1:30            |
| P5 · nombre pendiente | Proceso, eventos y notificaciones | 1:30            |
| P1 · nombre pendiente | Demo integrada                    | 2:30            |
| P6 · Francisco        | Reportes, validación y cierre     | 1:30            |

## Demostración local de P6

Desde reporting-service ejecutar npm ci y npm run demo. Abrir /api-docs/.

1. Consultar /health y comprobar UP.
2. Mostrar el resumen: un ticket P1 escalado y un SLA vencido.
3. Mostrar /api/reportes/tickets?prioridad=P1&estado=ESCALADO.
4. Mostrar /api/reportes/sla y explicar que un ticket resuelto deja de contar
   como vencido y pasa a contribuir al porcentaje de cumplimiento.
5. Para evidencias reproducibles, detener con Ctrl+C y ejecutar
   npm run verificar:demo. El archivo evidencias.json incluye la repetición,
   el evento viejo y la resolución tardía.

La demo integrada debe ejecutarla P1 cuando estén disponibles gateway, productores
y demás servicios. La demo local usa eventos de ejemplo y no verifica al LLM.

## Preguntas posibles

- ¿Por qué CQRS? Para optimizar consultas y evitar acoplar reportes al agregado.
- ¿Qué pasa con un duplicado? Devuelve 202 DUPLICADO si ya se completó.
- ¿Qué pasa si falla después de guardar? Permanece PENDIENTE y el productor reintenta.
- ¿Qué pasa con un evento viejo? Se registra sin sobrescribir el snapshot más nuevo.
- ¿Requiere transacciones? No; usa índices únicos y operaciones atómicas por documento.
- ¿Qué garantiza el SLA? Solo calcula sobre prioridades y límites emitidos por el dominio.
- ¿Hay consistencia inmediata? No; las vistas reflejan eventos después de recibirlos.
- ¿Cómo se migraría a una cola? Sustituyendo el transporte de entrada y conservando
  sobre, servicio de eventos, idempotencia y proyectores.

## Mensaje preparado para la revisión del equipo

“Les comparto los materiales de P6 y el diagrama de componentes. Por favor revisen
sus secciones: P1 14–15, P2 7–9, P3 3–5, P4 11, P5 10–12 y P6 1, 2, 6, 13 y 16.
Confirmemos el índice final, la asignación de nombres a roles y los contratos de
eventos antes de exportar el informe completo. Propongo un ensayo de exposición
y otro de demo con fallos, registrando tiempos y cambios pendientes.”

Este texto queda preparado para que Francisco lo envíe; no fue enviado.

## Registro de ensayo (sin completar)

| Ensayo                    | Fecha acordada | Tiempo real | Incidencias | Responsable del ajuste |
| ------------------------- | -------------- | ----------- | ----------- | ---------------------- |
| Exposición y transiciones | Pendiente      | Pendiente   | Pendiente   | Pendiente              |
| Demo normal y fallback    | Pendiente      | Pendiente   | Pendiente   | Pendiente              |

Plan B: usar las evidencias locales de P6 y preparar un video de la demo integrada
cuando el equipo disponga de todos los servicios.
