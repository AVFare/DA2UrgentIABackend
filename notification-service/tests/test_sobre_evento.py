import pytest
from pydantic import ValidationError

from app.schemas.evento import EJEMPLO_SOBRE, InputEvento
from tests.conftest import evento, sobre


def test_el_ejemplo_del_contrato_es_valido():
    e = InputEvento.model_validate(EJEMPLO_SOBRE)
    assert e.event_type == "TicketEscalado"
    assert e.payload.ticket.titulo == "No puede ingresar nadie"


def test_el_dict_para_los_services_queda_en_camel_case_y_fechas_utc():
    e = evento("TicketEscalado", fechaLimiteSla="2026-10-05T12:03:11.500-03:00")
    assert e["eventId"] and e["payload"]["ticket"]["ticketId"] == "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    assert e["payload"]["ticket"]["fechaLimiteSla"] == "2026-10-05T15:03:11Z"


def test_ticket_sin_clasificar_acepta_nulos():
    datos = sobre(
        "TicketCreado", estado="NUEVO", prioridad=None, categoria=None, moduloAfectado=None, fechaLimiteSla=None
    )
    del datos["payload"]["estadoAnterior"], datos["payload"]["motivo"]
    assert InputEvento.model_validate(datos).payload.ticket.prioridad is None


@pytest.mark.parametrize(
    "campo, valor",
    [("eventType", "TicketInventado"), ("eventId", "no-es-uuid"), ("version", 0), ("correlationId", "")],
)
def test_sobre_invalido(campo, valor):
    with pytest.raises(ValidationError):
        InputEvento.model_validate({**sobre(), campo: valor})


def test_titulo_fuera_de_rango():
    with pytest.raises(ValidationError):
        InputEvento.model_validate(sobre(titulo="abc"))
