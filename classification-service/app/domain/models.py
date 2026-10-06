"""Modelo de dominio: la Clasificacion que sugiere la IA (CONTEXTO_PROYECTO.md, secciones 7 y 9.3).

Es Python puro (sin FastAPI, Pydantic ni Mongo). Sus invariantes se validan al construirla,
asi que no puede existir una Clasificacion invalida, venga del LLM real o del mock.
"""

import math
from dataclasses import dataclass
from enum import StrEnum

from app.domain.enums import Categoria, Impacto, ModuloAfectado, Urgencia

JUSTIFICACION_MAX = 300


class ClasificacionInvalidaError(ValueError):
    """La sugerencia de la IA no cumple las reglas del dominio."""


@dataclass(frozen=True)
class Clasificacion:
    """Lo que sugiere la IA. No incluye la prioridad: esa la decide el dominio de tickets."""

    categoria: Categoria
    urgencia: Urgencia
    impacto: Impacto
    modulo_afectado: ModuloAfectado
    requiere_escalamiento: bool
    confianza: float
    justificacion: str

    def __post_init__(self) -> None:
        # Acepta los enums o su texto (por ejemplo "ALTA"), y siempre guarda el enum.
        self._fijar("categoria", _enum(Categoria, self.categoria, "categoria"))
        self._fijar("urgencia", _enum(Urgencia, self.urgencia, "urgencia"))
        self._fijar("impacto", _enum(Impacto, self.impacto, "impacto"))
        self._fijar("modulo_afectado", _enum(ModuloAfectado, self.modulo_afectado, "moduloAfectado"))

        if not isinstance(self.requiere_escalamiento, bool):
            raise ClasificacionInvalidaError("requiereEscalamiento tiene que ser true o false")

        # bool es subclase de int en Python: se rechaza a proposito.
        if isinstance(self.confianza, bool) or not isinstance(self.confianza, (int, float)):
            raise ClasificacionInvalidaError("confianza tiene que ser un numero")
        if math.isnan(self.confianza) or not 0 <= self.confianza <= 1:
            raise ClasificacionInvalidaError(f"confianza tiene que estar entre 0 y 1, vino {self.confianza}")
        self._fijar("confianza", float(self.confianza))

        if not isinstance(self.justificacion, str) or not self.justificacion.strip():
            raise ClasificacionInvalidaError("falta la justificacion")
        justificacion = self.justificacion.strip()
        if len(justificacion) > JUSTIFICACION_MAX:
            raise ClasificacionInvalidaError(
                f"la justificacion tiene {len(justificacion)} caracteres; el maximo es {JUSTIFICACION_MAX}"
            )
        self._fijar("justificacion", justificacion)

    def _fijar(self, campo: str, valor: object) -> None:
        object.__setattr__(self, campo, valor)


def _enum[E: StrEnum](tipo: type[E], valor: object, campo: str) -> E:
    if isinstance(valor, tipo):
        return valor
    try:
        return tipo(valor)
    except ValueError:
        permitidos = ", ".join(tipo)
        raise ClasificacionInvalidaError(
            f"{campo} tiene un valor fuera de la lista: {valor!r} (permitidos: {permitidos})"
        ) from None
