"""Los mensajes que arma y acepta el servicio cumplen contracts/messaging/."""

import copy
import json
import re
from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID

import pytest
from jsonschema import Draft202012Validator, FormatChecker
from pydantic import ValidationError

from app.domain.models import RegistroClasificacion
from app.infrastructure.messaging.mensajes import ClasificacionGenerada, ClasificacionSolicitada
from tests.fakes import CLASIFICACION_CRITICA

CONTRATO = Path(__file__).resolve().parents[2] / "contracts" / "messaging"
VALIDADOR = Draft202012Validator(
    json.loads((CONTRATO / "clasificacion-mensajes.schema.json").read_text(encoding="utf-8")),
    format_checker=FormatChecker(),
)
_BLOQUES_JSON = re.findall(r"```json\n(.*?)```", (CONTRATO / "clasificacion.md").read_text(encoding="utf-8"), re.S)
EJEMPLOS = {ejemplo["eventType"]: ejemplo for ejemplo in map(json.loads, _BLOQUES_JSON)}

SOLICITUD_ID = UUID("0b7e1f0a-6c1d-4f6e-9a55-2d1c3e4f5a6b")
REGISTRO = RegistroClasificacion(
    id="66f7c2a1e4b0a1b2c3d4e5f6",
    ticket_id=UUID("7c9e6679-7425-40de-944b-e07fc1f90ae7"),
    clasificacion=CLASIFICACION_CRITICA,
    proveedor="mock",
    modelo="mock-v1",
    version_prompt="full",
    latencia_ms=812,
    fecha=datetime(2026, 11, 14, 14, 3, 11, tzinfo=timezone.utc),
    texto_enmascarado="Login\nMi mail es [EMAIL]",
    respuesta_cruda='{"categoria": "INCIDENTE"}',
    solicitud_id=SOLICITUD_ID,
)


def test_los_ejemplos_del_contrato_cumplen_el_esquema():
    assert set(EJEMPLOS) == {"ClasificacionSolicitada", "ClasificacionGenerada"}
    for ejemplo in EJEMPLOS.values():
        VALIDADOR.validate(ejemplo)


def test_acepta_la_solicitud_de_ejemplo_del_contrato():
    solicitud = ClasificacionSolicitada.model_validate(EJEMPLOS["ClasificacionSolicitada"]).a_solicitud()

    assert solicitud.solicitud_id == SOLICITUD_ID
    assert solicitud.ticket_id == REGISTRO.ticket_id
    assert solicitud.titulo == "No puede ingresar nadie"
    assert solicitud.correlation_id == "a8e1b2c3-0000-4000-8000-000000000000"


@pytest.mark.parametrize("titulo", ["abc", "x" * 121])
def test_rechaza_una_solicitud_con_el_titulo_fuera_de_rango(titulo):
    mensaje = copy.deepcopy(EJEMPLOS["ClasificacionSolicitada"])
    mensaje["payload"]["titulo"] = titulo

    with pytest.raises(ValidationError):
        ClasificacionSolicitada.model_validate(mensaje)
    assert not VALIDADOR.is_valid(mensaje)


def test_la_respuesta_que_se_publica_cumple_el_esquema():
    mensaje = json.loads(ClasificacionGenerada.desde_registro(REGISTRO, "correlacion-1").a_json())

    VALIDADOR.validate(mensaje)
    assert mensaje["eventType"] == "ClasificacionGenerada"
    assert mensaje["source"] == "classification-service"
    assert mensaje["correlationId"] == "correlacion-1"
    assert mensaje["occurredAt"] == "2026-11-14T14:03:11Z"
    assert mensaje["payload"]["solicitudId"] == str(SOLICITUD_ID)
    assert mensaje["payload"]["clasificacion"]["moduloAfectado"] == "AUTENTICACION"
    assert set(mensaje["payload"]["clasificacion"]) == set(EJEMPLOS["ClasificacionGenerada"]["payload"]["clasificacion"])


def test_la_respuesta_de_una_misma_solicitud_lleva_siempre_el_mismo_event_id():
    primera = ClasificacionGenerada.desde_registro(REGISTRO, "correlacion-1")
    segunda = ClasificacionGenerada.desde_registro(REGISTRO, "correlacion-1")
    otra_solicitud = replace(REGISTRO, solicitud_id=UUID("11111111-1111-4111-8111-111111111111"))

    assert primera.event_id == segunda.event_id
    assert primera.event_id != ClasificacionGenerada.desde_registro(otra_solicitud, "correlacion-1").event_id


def test_un_registro_que_no_vino_de_la_cola_no_se_puede_publicar():
    with pytest.raises(ValueError):
        ClasificacionGenerada.desde_registro(replace(REGISTRO, solicitud_id=None), "correlacion-1")
