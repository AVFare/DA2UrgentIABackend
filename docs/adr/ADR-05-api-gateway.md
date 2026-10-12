# ADR-05: API Gateway como única entrada

**Estado:** aceptada

## Contexto

Con seis servicios, repetir la validación del token, la autorización y la documentación en cada uno duplica código y deja huecos de seguridad.

## Decisión

`api-gateway` (Spring Cloud Gateway) es el único servicio con un puerto publicado (8080). Su cadena de filtros:

1. Genera o propaga `X-Correlation-Id`.
2. Valida el JWT (HS256 con `JWT_SECRET`), salvo en el login y la documentación.
3. Propaga `X-User-Id` y `X-User-Rol` a los servicios.
4. Autoriza por rol, por ejemplo ADMIN para el alta de usuarios y para `/api/clasificaciones`.

Además expone un Swagger que agrega los seis servicios. `/api/eventos` no se rutea: los eventos solo viajan por la red interna.

## Consecuencias

- La seguridad y la documentación viven en un solo lugar.
- Los servicios internos confían en los headers del gateway, por eso no se publican sus puertos fuera de la red de Compose.
- El gateway es un punto único de falla.
