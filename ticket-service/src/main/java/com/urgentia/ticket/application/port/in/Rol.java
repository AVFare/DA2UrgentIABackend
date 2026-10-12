package com.urgentia.ticket.application.port.in;

/** Roles del sistema (seccion 7). Llegan en el header X-User-Rol que pone el gateway. */
public enum Rol {
    SOLICITANTE,
    AGENTE,
    ADMIN
}
