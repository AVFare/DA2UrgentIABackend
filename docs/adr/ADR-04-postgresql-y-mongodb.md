# ADR-04: PostgreSQL para tickets y usuarios; MongoDB para el resto

**Estado:** aceptada

## Contexto

Los tickets y los usuarios tienen invariantes y cambios que deben ser transaccionales. Las clasificaciones, las notificaciones y los reportes tienen una forma más flexible o son vistas de lectura.

## Decisión

- PostgreSQL 16: `tickets_db` y `users_db`, con migraciones de Flyway.
- MongoDB 7: `classification_db`, `notifications_db` y `reporting_db`.

## Consecuencias

- Los tickets usan transacciones y control de concurrencia optimista (`@Version`), así dos cambios simultáneos sobre el mismo ticket no se pisan.
- Mongo guarda documentos de forma variable sin migraciones: la respuesta cruda del LLM, las notificaciones por canal y la vista `ticket_view`.
- Compose levanta dos motores de base de datos.
