import pytest

from app.exceptions.api_exception import ApiException
from app.services.evento_service import resolver_destino
from tests.conftest import evento

AGENTE = "b1c2d3e4-0000-4000-8000-000000000002"
SOLICITANTE = "b1c2d3e4-0000-4000-8000-000000000004"


def test_ticket_escalado_va_a_la_guardia_por_email():
    assert resolver_destino(evento("TicketEscalado")) == ("GRUPO:GUARDIA", "EMAIL_SIMULADO")


def test_ticket_asignado_va_al_agente_por_canal_interno():
    e = evento("TicketAsignado", estado="ASIGNADO", agenteAsignadoId=AGENTE)
    assert resolver_destino(e) == (f"USUARIO:{AGENTE}", "INTERNA")


def test_ticket_resuelto_va_al_solicitante_por_email():
    e = evento("TicketResuelto", estado="RESUELTO", agenteAsignadoId=AGENTE)
    assert resolver_destino(e) == (f"USUARIO:{SOLICITANTE}", "EMAIL_SIMULADO")


def test_ticket_asignado_sin_agente_es_invalido():
    with pytest.raises(ApiException) as error:
        resolver_destino(evento("TicketAsignado", estado="ASIGNADO", agenteAsignadoId=None))
    assert error.value.status == 400
    assert error.value.detalles[0]["campo"] == "payload.ticket.agenteAsignadoId"


@pytest.mark.parametrize("tipo", ["TicketCreado", "TicketClasificado", "TicketEstadoCambiado"])
def test_los_demas_eventos_no_notifican(tipo):
    assert resolver_destino(evento(tipo)) is None
