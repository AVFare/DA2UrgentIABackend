"""Valores exactos del contexto (seccion 7). No se inventan valores."""

from enum import StrEnum


class TipoEvento(StrEnum):
    TICKET_CREADO = "TicketCreado"
    TICKET_CLASIFICADO = "TicketClasificado"
    TICKET_ESCALADO = "TicketEscalado"
    TICKET_ASIGNADO = "TicketAsignado"
    TICKET_ESTADO_CAMBIADO = "TicketEstadoCambiado"
    TICKET_RESUELTO = "TicketResuelto"


class CanalNotificacion(StrEnum):
    EMAIL_SIMULADO = "EMAIL_SIMULADO"
    INTERNA = "INTERNA"


class EstadoNotificacion(StrEnum):
    ENVIADA = "ENVIADA"
    FALLIDA = "FALLIDA"


class ResultadoEvento(StrEnum):
    PROCESADO = "PROCESADO"
    DUPLICADO = "DUPLICADO"
    IGNORADO = "IGNORADO"


class EstadoTicket(StrEnum):
    NUEVO = "NUEVO"
    PENDIENTE_CLASIFICACION = "PENDIENTE_CLASIFICACION"
    CLASIFICADO = "CLASIFICADO"
    ASIGNADO = "ASIGNADO"
    EN_CURSO = "EN_CURSO"
    ESCALADO = "ESCALADO"
    RESUELTO = "RESUELTO"
    CERRADO = "CERRADO"


class Prioridad(StrEnum):
    P1 = "P1"
    P2 = "P2"
    P3 = "P3"
    P4 = "P4"


class Categoria(StrEnum):
    INCIDENTE = "INCIDENTE"
    SOLICITUD = "SOLICITUD"
    CONSULTA = "CONSULTA"
    BUG = "BUG"


class ModuloAfectado(StrEnum):
    AUTENTICACION = "AUTENTICACION"
    FACTURACION = "FACTURACION"
    PAGOS = "PAGOS"
    REPORTES = "REPORTES"
    INFRAESTRUCTURA = "INFRAESTRUCTURA"
    BASE_DE_DATOS = "BASE_DE_DATOS"
    INTEGRACIONES = "INTEGRACIONES"
    OTRO = "OTRO"
