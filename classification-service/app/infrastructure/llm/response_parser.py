"""Anti-Corruption Layer: traduce el texto que devuelve el LLM a la Clasificacion del dominio.

Tolera texto alrededor del JSON (por ejemplo, un bloque de codigo); todo lo demas tiene que
cumplir el formato pedido en el prompt y las reglas del dominio.
"""

import json

from app.application.errors import LlmRespuestaInvalidaError
from app.domain.models import Clasificacion, ClasificacionInvalidaError

_CAMPOS = {
    "categoria": "categoria",
    "urgencia": "urgencia",
    "impacto": "impacto",
    "moduloAfectado": "modulo_afectado",
    "requiereEscalamiento": "requiere_escalamiento",
    "confianza": "confianza",
    "justificacion": "justificacion",
}


def parsear_clasificacion(texto: str) -> Clasificacion:
    datos = _extraer_objeto_json(texto)
    faltantes = [campo for campo in _CAMPOS if campo not in datos]
    if faltantes:
        raise LlmRespuestaInvalidaError(f"faltan campos: {', '.join(faltantes)}", texto)
    try:
        return Clasificacion(**{atributo: datos[campo] for campo, atributo in _CAMPOS.items()})
    except ClasificacionInvalidaError as error:
        raise LlmRespuestaInvalidaError(str(error), texto) from error


def _extraer_objeto_json(texto: str) -> dict:
    inicio, fin = texto.find("{"), texto.rfind("}")
    if inicio == -1 or fin < inicio:
        raise LlmRespuestaInvalidaError("la respuesta no tiene un objeto JSON", texto)
    try:
        datos = json.loads(texto[inicio:fin + 1])
    except json.JSONDecodeError as error:
        raise LlmRespuestaInvalidaError(f"el JSON no es valido ({error.msg})", texto) from error
    if not isinstance(datos, dict):
        raise LlmRespuestaInvalidaError("la respuesta no es un objeto JSON", texto)
    return datos
