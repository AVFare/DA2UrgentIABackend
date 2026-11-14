"""Los mensajes y la topologia del servicio cumplen el contrato de mensajeria.

Contrato: contracts/events/clasificacion-events.schema.json y contracts/messaging/rabbitmq-definitions.json.
"""

import copy
import json
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID

import pytest
from jsonschema import Draft202012Validator
from pydantic import ValidationError

from app.application.errors import LlmNoDisponibleError, LlmRespuestaInvalidaError, LlmTimeoutError
from app.application.solicitud import SolicitudDeClasificacion
from app.domain.models import RegistroClasificacion
from app.infrastructure.messaging.mensajes import ClasificacionGenerada, ClasificacionSolicitada
from app.infrastructure.messaging.topologia import Topologia
from tests.fakes import CLASIFICACION_CRITICA

CONTRATOS = Path(__file__).resolve().parents[2] / "contracts"
ESQUEMA = json.loads((CONTRATOS / "events" / "clasificacion-events.schema.json").read_text(encoding="utf-8"))
VALIDADOR = Draft202012Validator(ESQUEMA)
DEFINICIONES = json.loads((CONTRATOS / "messaging" / "rabbitmq-definitions.json").read_text(encoding="utf-8"))

EJEMPLO_SOLICITADA = next(e for e in ESQUEMA["examples"] if e["eventType"] == "ClasificacionSolicitada")
EJEMPLO_CLASIFICADO = next(e for e in ESQUEMA["examples"] if e["payload"].get("resultado") == "CLASIFICADO")

SOLICITUD = SolicitudDeClasificacion(
    solicitud_id=UUID("0b7e1f0a-6c1d-4f6e-9a55-2d1c3e4f5a6b"),
    ticket_id=UUID("7c9e6679-7425-40de-944b-e07fc1f90ae7"),
    titulo="No puede ingresar nadie",
    descripcion="Producción caída, todos los usuarios bloqueados",
    correlation_id="correlacion-1",
)
REGISTRO = RegistroClasificacion(
    id="66f7c2a1e4b0a1b2c3d4e5f6",
    ticket_id=SOLICITUD.ticket_id,
    clasificacion=CLASIFICACION_CRITICA,
    proveedor="mock",
    modelo="mock-v1",
    version_prompt="full",
    latencia_ms=812,
    fecha=datetime(2026, 11, 14, 14, 3, 11, tzinfo=timezone.utc),
    texto_enmascarado="Login\nMi mail es [EMAIL]",
    respuesta_cruda='{"categoria": "INCIDENTE"}',
    solicitud_id=SOLICITUD.solicitud_id,
)


def test_acepta_la_solicitud_de_ejemplo_del_contrato():
    solicitud = ClasificacionSolicitada.model_validate(EJEMPLO_SOLICITADA).a_solicitud()

    assert solicitud.solicitud_id == UUID(EJEMPLO_SOLICITADA["eventId"])
    assert solicitud.ticket_id == UUID(EJEMPLO_SOLICITADA["payload"]["ticketId"])
    assert solicitud.titulo == "No puede ingresar nadie"
    assert solicitud.correlation_id == EJEMPLO_SOLICITADA["correlationId"]


@pytest.mark.parametrize("titulo", ["abc", "x" * 121])
def test_rechaza_una_solicitud_con_el_titulo_fuera_de_rango(titulo):
    mensaje = copy.deepcopy(EJEMPLO_SOLICITADA)
    mensaje["payload"]["titulo"] = titulo

    with pytest.raises(ValidationError):
        ClasificacionSolicitada.model_validate(mensaje)
    assert not VALIDADOR.is_valid(mensaje)


def test_la_clasificacion_que_se_publica_cumple_el_esquema():
    mensaje = json.loads(ClasificacionGenerada.clasificado(SOLICITUD, REGISTRO).a_json())

    VALIDADOR.validate(mensaje)
    assert mensaje["eventType"] == "ClasificacionGenerada"
    assert mensaje["source"] == "classification-service"
    assert mensaje["correlationId"] == "correlacion-1"
    assert mensaje["occurredAt"] == "2026-11-14T14:03:11Z"
    assert mensaje["payload"]["ticketId"] == str(SOLICITUD.ticket_id)
    assert mensaje["payload"]["resultado"] == "CLASIFICADO"
    assert "error" not in mensaje["payload"]
    assert mensaje["payload"]["clasificacion"]["moduloAfectado"] == "AUTENTICACION"
    assert set(mensaje["payload"]["clasificacion"]) == set(EJEMPLO_CLASIFICADO["payload"]["clasificacion"])


@pytest.mark.parametrize(
    ("error", "codigo"),
    [
        (LlmTimeoutError(5000), "LLM_TIMEOUT"),
        (LlmRespuestaInvalidaError("faltan campos"), "LLM_RESPUESTA_INVALIDA"),
        (LlmNoDisponibleError("groq respondio HTTP 503"), "LLM_RESPUESTA_INVALIDA"),
    ],
)
def test_el_error_que_se_publica_cumple_el_esquema(error, codigo):
    mensaje = json.loads(ClasificacionGenerada.con_error(SOLICITUD, error).a_json())

    VALIDADOR.validate(mensaje)
    assert mensaje["payload"]["resultado"] == "ERROR"
    assert mensaje["payload"]["error"]["codigo"] == codigo
    assert "clasificacion" not in mensaje["payload"]


def test_la_respuesta_de_una_misma_solicitud_lleva_siempre_el_mismo_event_id():
    primera = ClasificacionGenerada.clasificado(SOLICITUD, REGISTRO)
    segunda = ClasificacionGenerada.clasificado(SOLICITUD, REGISTRO)
    otra_solicitud = ClasificacionGenerada.clasificado(
        SolicitudDeClasificacion(**{**SOLICITUD.__dict__, "solicitud_id": UUID("11111111-1111-4111-8111-111111111111")}),
        REGISTRO,
    )
    error = ClasificacionGenerada.con_error(SOLICITUD, LlmTimeoutError(5000))

    assert primera.event_id == segunda.event_id
    assert primera.event_id != otra_solicitud.event_id
    assert primera.event_id != error.event_id


def test_la_topologia_que_declara_el_servicio_es_la_del_contrato():
    topologia = Topologia()
    colas = {cola["name"]: cola for cola in DEFINICIONES["queues"]}
    exchanges = {exchange["name"]: exchange["type"] for exchange in DEFINICIONES["exchanges"]}
    bindings = {(b["source"], b["destination"], b["routing_key"]) for b in DEFINICIONES["bindings"]}

    assert exchanges[topologia.exchange] == "direct"
    assert exchanges[topologia.exchange_de_muertos] == "direct"
    assert colas[topologia.cola_de_solicitudes]["arguments"] == topologia.argumentos_de_la_cola()
    assert colas[topologia.cola_de_muertos]["arguments"] == {"x-queue-type": "quorum"}
    assert (topologia.exchange, topologia.cola_de_solicitudes, topologia.clave_solicitada) in bindings
    assert (topologia.exchange_de_muertos, topologia.cola_de_muertos, topologia.cola_de_muertos) in bindings
    assert any(b[0] == topologia.exchange and b[2] == topologia.clave_generada for b in bindings)
