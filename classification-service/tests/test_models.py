import math

import pytest

from app.domain.enums import Categoria, Impacto, ModuloAfectado, Urgencia
from app.domain.models import JUSTIFICACION_MAX, Clasificacion, ClasificacionInvalidaError

VALIDA = {
    "categoria": "INCIDENTE",
    "urgencia": "ALTA",
    "impacto": "ALTO",
    "modulo_afectado": "AUTENTICACION",
    "requiere_escalamiento": True,
    "confianza": 0.93,
    "justificacion": "Caída total del login en producción que afecta a todos los usuarios",
}


def test_crea_una_clasificacion_valida_y_convierte_los_textos_a_enums():
    clasificacion = Clasificacion(**VALIDA)

    assert clasificacion.categoria is Categoria.INCIDENTE
    assert clasificacion.urgencia is Urgencia.ALTA
    assert clasificacion.impacto is Impacto.ALTO
    assert clasificacion.modulo_afectado is ModuloAfectado.AUTENTICACION
    assert clasificacion.confianza == 0.93


def test_acepta_los_enums_directamente():
    clasificacion = Clasificacion(**{**VALIDA, "categoria": Categoria.BUG})

    assert clasificacion.categoria is Categoria.BUG


@pytest.mark.parametrize("campo, valor", [
    ("categoria", "PROBLEMA"),
    ("urgencia", "URGENTE"),
    ("impacto", "alto"),
    ("modulo_afectado", "VENTAS"),
    ("categoria", None),
])
def test_rechaza_valores_fuera_de_la_lista(campo, valor):
    with pytest.raises(ClasificacionInvalidaError, match="fuera de la lista"):
        Clasificacion(**{**VALIDA, campo: valor})


@pytest.mark.parametrize("confianza", [-0.01, 1.01, math.nan, "0.9", None, True])
def test_rechaza_confianza_invalida(confianza):
    with pytest.raises(ClasificacionInvalidaError, match="confianza"):
        Clasificacion(**{**VALIDA, "confianza": confianza})


@pytest.mark.parametrize("confianza", [0, 1, 0.5])
def test_acepta_confianza_en_los_bordes(confianza):
    assert Clasificacion(**{**VALIDA, "confianza": confianza}).confianza == float(confianza)


@pytest.mark.parametrize("valor", ["true", 1, None])
def test_rechaza_requiere_escalamiento_que_no_es_booleano(valor):
    with pytest.raises(ClasificacionInvalidaError, match="requiereEscalamiento"):
        Clasificacion(**{**VALIDA, "requiere_escalamiento": valor})


@pytest.mark.parametrize("justificacion", ["", "   ", None])
def test_rechaza_justificacion_vacia(justificacion):
    with pytest.raises(ClasificacionInvalidaError, match="justificacion"):
        Clasificacion(**{**VALIDA, "justificacion": justificacion})


def test_rechaza_justificacion_demasiado_larga():
    with pytest.raises(ClasificacionInvalidaError, match="maximo"):
        Clasificacion(**{**VALIDA, "justificacion": "x" * (JUSTIFICACION_MAX + 1)})


def test_acepta_justificacion_del_largo_maximo_y_recorta_espacios():
    clasificacion = Clasificacion(**{**VALIDA, "justificacion": "  " + "x" * JUSTIFICACION_MAX + "  "})

    assert clasificacion.justificacion == "x" * JUSTIFICACION_MAX


def test_es_inmutable():
    clasificacion = Clasificacion(**VALIDA)

    with pytest.raises(AttributeError):
        clasificacion.confianza = 0.1
