import json

import pytest

from app.application.errors import LlmRespuestaInvalidaError
from app.domain.enums import ModuloAfectado
from app.infrastructure.llm.response_parser import parsear_clasificacion

VALIDA = {
    "categoria": "BUG",
    "urgencia": "MEDIA",
    "impacto": "BAJO",
    "moduloAfectado": "FACTURACION",
    "requiereEscalamiento": False,
    "confianza": 0.85,
    "justificacion": "Error de cálculo puntual en una factura",
}


def test_parsea_un_json_valido():
    clasificacion = parsear_clasificacion(json.dumps(VALIDA, ensure_ascii=False))

    assert clasificacion.modulo_afectado is ModuloAfectado.FACTURACION
    assert clasificacion.confianza == 0.85


@pytest.mark.parametrize("texto", [
    "```json\n" + json.dumps(VALIDA) + "\n```",
    "Aquí está la clasificación: " + json.dumps(VALIDA) + " Espero que sirva.",
    json.dumps({**VALIDA, "prioridad": "P1", "otraCosa": 1}),
])
def test_tolera_texto_alrededor_y_campos_de_mas(texto):
    assert parsear_clasificacion(texto).justificacion == VALIDA["justificacion"]


@pytest.mark.parametrize("texto, mensaje", [
    ("No sé qué responder", "no tiene un objeto JSON"),
    ('{"categoria": "BUG",}', "el JSON no es valido"),
    (json.dumps({k: v for k, v in VALIDA.items() if k != "impacto"}), "faltan campos: impacto"),
    (json.dumps({**VALIDA, "urgencia": "URGENTE"}), "urgencia tiene un valor fuera de la lista"),
    (json.dumps({**VALIDA, "confianza": 1.5}), "confianza"),
    (json.dumps({**VALIDA, "justificacion": "x" * 301}), "justificacion"),
    ("[1, 2]", "no tiene un objeto JSON"),
])
def test_rechaza_respuestas_invalidas(texto, mensaje):
    with pytest.raises(LlmRespuestaInvalidaError, match=mensaje) as error:
        parsear_clasificacion(texto)
    assert error.value.texto_crudo == texto
