"""MockLlmProvider: clasifica por palabras clave, sin internet (CONTEXTO_PROYECTO.md, 12.1).

Es el proveedor por defecto para desarrollar, para los tests, el CI y el plan B de la demo.
Devuelve el mismo JSON que se le pide al LLM real, asi la respuesta pasa por el mismo
parser (ACL) que la de cualquier otro proveedor.

Las palabras clave se buscan al inicio de una palabra del texto, en minusculas y sin tildes:
"pago" encuentra "pagos", pero "red" no encuentra "credito".
"""

import json
import re
import unicodedata
from dataclasses import dataclass

from app.application.ports.llm_provider import PedidoLlm, RespuestaLlm
from app.domain.enums import Categoria, Impacto, ModuloAfectado, Urgencia
from app.infrastructure.llm.response_parser import parsear_clasificacion

NOMBRE = "mock"
MODELO = "mock-v1"
CONFIANZA = 0.7


@dataclass(frozen=True)
class _Regla:
    descripcion: str
    categoria: Categoria
    urgencia: Urgencia
    impacto: Impacto
    requiere_escalamiento: bool


_CAIDA = ("caida", "no funciona", "lento")
_ALCANCE_MASIVO = ("produccion", "todos", "nadie", "toda la empresa")
_ERROR = ("error", "no puedo", "falla")
_VARIOS_AFECTADOS = ("companeros", "area", "otros")
_BUG = ("bug", "no hace nada", "salio mal", "incorrect")
_SOLICITUD = ("necesito", "solicito", "alta de", "dar de alta")
_CONSULTA = ("como", "consulta")

_MODULOS: tuple[tuple[ModuloAfectado, tuple[str, ...]], ...] = (
    (ModuloAfectado.AUTENTICACION, ("login", "contrasena", "ingresar", "usuario")),
    (ModuloAfectado.FACTURACION, ("factura",)),
    (ModuloAfectado.PAGOS, ("pago", "tarjeta")),
    (ModuloAfectado.REPORTES, ("reporte", "excel", "pdf")),
    (ModuloAfectado.INFRAESTRUCTURA, ("servidor", "disco", "red")),
    (ModuloAfectado.BASE_DE_DATOS, ("base de datos", "consultas")),
    (ModuloAfectado.INTEGRACIONES, ("banco", "sincronizacion", "integracion")),
)


def normalizar(texto: str) -> str:
    """Minusculas y sin tildes (la ñ queda como n)."""
    sin_tildes = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in sin_tildes if not unicodedata.combining(c)).lower()


def _contiene(texto: str, palabras: tuple[str, ...]) -> bool:
    return any(re.search(r"\b" + re.escape(palabra), texto) for palabra in palabras)


def _elegir_regla(texto: str) -> _Regla:
    """Las reglas se evaluan en orden; gana la primera que aplica."""
    if _contiene(texto, _CAIDA) and _contiene(texto, _ALCANCE_MASIVO):
        return _Regla("caida o lentitud que afecta a muchos usuarios o a produccion",
                      Categoria.INCIDENTE, Urgencia.ALTA, Impacto.ALTO, True)
    if _contiene(texto, _ERROR):
        if _contiene(texto, _VARIOS_AFECTADOS):
            return _Regla("error que afecta a varios usuarios",
                          Categoria.INCIDENTE, Urgencia.MEDIA, Impacto.MEDIO, False)
        return _Regla("error que afecta a un usuario",
                      Categoria.INCIDENTE, Urgencia.MEDIA, Impacto.BAJO, False)
    if _contiene(texto, _BUG):
        return _Regla("comportamiento incorrecto puntual",
                      Categoria.BUG, Urgencia.MEDIA, Impacto.BAJO, False)
    if _contiene(texto, _SOLICITUD):
        return _Regla("pedido de algo nuevo",
                      Categoria.SOLICITUD, Urgencia.BAJA, Impacto.BAJO, False)
    if _contiene(texto, _CONSULTA) or "?" in texto:
        return _Regla("pregunta de uso",
                      Categoria.CONSULTA, Urgencia.BAJA, Impacto.BAJO, False)
    return _Regla("ninguna regla aplico",
                  Categoria.CONSULTA, Urgencia.BAJA, Impacto.BAJO, False)


def _elegir_modulo(texto: str) -> ModuloAfectado:
    for modulo, palabras in _MODULOS:
        if _contiene(texto, palabras):
            return modulo
    return ModuloAfectado.OTRO


def clasificar_por_reglas(titulo: str, descripcion: str) -> dict:
    """Clasificacion en el mismo formato JSON que se le pide al LLM real."""
    texto = normalizar(f"{titulo} {descripcion}")
    regla = _elegir_regla(texto)
    return {
        "categoria": regla.categoria.value,
        "urgencia": regla.urgencia.value,
        "impacto": regla.impacto.value,
        "moduloAfectado": _elegir_modulo(texto).value,
        "requiereEscalamiento": regla.requiere_escalamiento,
        "confianza": CONFIANZA,
        "justificacion": f"Regla del mock: {regla.descripcion}",
    }


class MockLlmProvider:
    """Implementa el puerto LlmProvider sin llamar a ningun servicio externo."""

    nombre = NOMBRE

    async def consultar(self, pedido: PedidoLlm) -> RespuestaLlm:
        texto = json.dumps(clasificar_por_reglas(pedido.titulo, pedido.descripcion), ensure_ascii=False)
        return RespuestaLlm(clasificacion=parsear_clasificacion(texto), modelo=MODELO, texto_crudo=texto)
