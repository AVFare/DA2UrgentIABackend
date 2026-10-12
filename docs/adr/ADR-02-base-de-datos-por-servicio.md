# ADR-02: Base de datos propia por servicio

**Estado:** aceptada

## Contexto

Si varios servicios leen las mismas tablas, un cambio de esquema los rompe a todos juntos y se termina con un "monolito distribuido".

## Decisión

Cada servicio tiene su propia base, con su propio usuario: `users_db`, `tickets_db`, `classification_db`, `notifications_db` y `reporting_db`. Ningún servicio lee la base de otro. Los datos de otro contexto llegan por API o por eventos.

## Consecuencias

- Cada servicio se puede cambiar y desplegar por separado.
- `reporting-service` no puede hacer joins con los tickets: arma su propia vista (`ticket_view`) a partir de los eventos, con consistencia eventual.
- Las bases se crean con los scripts de `infra/postgres/init.sql` e `infra/mongo/init.js`.
