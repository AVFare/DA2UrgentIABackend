# ADR-08: Arquitectura hexagonal y DDD táctico solo en ticket-service

**Estado:** aceptada

## Contexto

`ticket-service` concentra las reglas del negocio: la matriz de prioridad, el SLA, las transiciones de estado y el escalamiento. Los demás servicios son subdominios de soporte con poca lógica propia.

## Decisión

- `ticket-service` usa puertos y adaptadores, con tres capas:
  - `domain`: Java puro, sin Spring. Incluye el agregado `Ticket`, los value objects, `TicketFactory`, `MatrizItilStrategy` y los eventos de dominio.
  - `application`: los casos de uso, con puertos de entrada y de salida.
  - `infrastructure`: los adaptadores REST, JPA, el cliente HTTP de la IA y el publicador de eventos.
- El resto de los servicios usa capas simples: controlador → servicio → repositorio.

## Consecuencias

- Las reglas del Core se testean sin levantar Spring.
- Cambiar HTTP por un broker en la parte 2 es cambiar un adaptador.
- La transacción se agrega con un decorador (`CasosDeUsoTransaccionales`), así la capa de aplicación no depende de Spring.
- Los servicios de soporte no cargan con la complejidad de la arquitectura hexagonal.
