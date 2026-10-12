"""Enums cerrados del lenguaje ubicuo (CONTEXTO_PROYECTO.md, seccion 7).

Los valores son exactamente los del contrato: no agregar ni renombrar sin acordarlo.
"""

from enum import StrEnum


class Categoria(StrEnum):
    INCIDENTE = "INCIDENTE"
    SOLICITUD = "SOLICITUD"
    CONSULTA = "CONSULTA"
    BUG = "BUG"


class Urgencia(StrEnum):
    ALTA = "ALTA"
    MEDIA = "MEDIA"
    BAJA = "BAJA"


class Impacto(StrEnum):
    ALTO = "ALTO"
    MEDIO = "MEDIO"
    BAJO = "BAJO"


class ModuloAfectado(StrEnum):
    AUTENTICACION = "AUTENTICACION"
    FACTURACION = "FACTURACION"
    PAGOS = "PAGOS"
    REPORTES = "REPORTES"
    INFRAESTRUCTURA = "INFRAESTRUCTURA"
    BASE_DE_DATOS = "BASE_DE_DATOS"
    INTEGRACIONES = "INTEGRACIONES"
    OTRO = "OTRO"
