# ADR-10: Servicio SOAP de consulta de estado de tickets

**Estado:** aceptada (Entrega 2). Implementada en `ticket-service` y publicada por el api-gateway.

## Contexto

El TP exige un servicio SOAP con WSDL. Para que tenga sentido y no sea un adorno, la justificación es un **sistema legado ficticio de un cliente que solo habla SOAP** y necesita saber en qué estado está un ticket, sin pasar por la API REST ni por el front.

Es un caso de uso de solo lectura y chico, que `ticket-service` ya sabe resolver: `ConsultarTicketsUseCase`.

## Decisión

- Una operación, `ConsultarEstadoTicket`. Entra el `ticketId`; sale `estado`, `prioridad` y `fechaLimiteSla`. La prioridad y el vencimiento faltan mientras el ticket no está clasificado.
- **Contract-first** (ADR-09): el WSDL 1.1 (SOAP 1.1, document/literal) y su XSD viven en `contracts/soap/`. Las clases Java del pedido y la respuesta se generan desde el XSD con JAXB, así el contrato sigue siendo la única fuente de verdad.
- Va **dentro de `ticket-service`**, como un adaptador de entrada más, en su propio paquete (`infrastructure/soap`) y con Spring-WS. Reusa el caso de uso de consulta; el dominio y la capa de aplicación no cambian (ADR-08).
- Los errores salen como **SOAP Fault** con un detalle `ErrorDetalle` (`codigo`, `mensaje`, `correlationId`): `NO_ENCONTRADO` y `VALIDACION` son del cliente (`soap:Client`); cualquier otra falla es `ERROR_INTERNO` (`soap:Server`) y no muestra detalles internos.
- Un SOLICITANTE solo ve sus tickets, igual que en el REST: el servicio usa `X-User-Id` y `X-User-Rol`, los headers que pone el gateway.
- Se publica en `/ws/tickets`, con el WSDL en `/ws/tickets.wsdl` y el XSD en `/ws/tickets-schema.xsd`.
- Entra por el api-gateway con el mismo JWT y los mismos roles que el REST: el gateway rutea `/ws/**` a `ticket-service`.
- La imagen Docker se arma solo con la carpeta del servicio y no ve `contracts/`, así que hay una **copia del contrato** en `ticket-service/src/main/resources/soap`. Un test (`ContratoSoapTest`) falla si deja de ser igual a `contracts/soap/`.

## Alternativas descartadas

- **Un servicio aparte:** otro Dockerfile, otro pipeline y otra base, para una consulta que `ticket-service` ya resuelve.
- **Code-first** (generar el WSDL desde el código): el contrato dejaría de escribirse antes del código y contradice el ADR-09.
- **SOAP 1.2:** más moderno, pero SOAP 1.1 es el que usa Spring-WS por defecto y el que soportan todas las herramientas de prueba (SoapUI, Postman).

## Consecuencias

- `ticket-service` suma dos dependencias (Spring-WS y JAXB) y un plugin de Maven que genera clases en cada build.
- Un SOAP Fault se responde con HTTP 500, como manda SOAP 1.1; no es un error del servicio.
- El contrato vive en dos lugares, protegidos por un test. Si cambia, hay que copiarlo.
- Es de solo lectura: no crea ni modifica tickets.
- Con la clasificación por cola (Entrega 2), un ticket recién creado devuelve `PENDIENTE_CLASIFICACION` sin prioridad ni SLA hasta que la IA responde; por eso esos dos campos son opcionales en el contrato.
