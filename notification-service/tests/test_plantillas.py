import pytest

from app.services.plantillas import PlantillaFactory
from tests.conftest import evento


def test_asunto_y_mensaje_de_ticket_escalado():
    asunto, mensaje = PlantillaFactory.para("TicketEscalado").generar(evento("TicketEscalado"))
    assert asunto == "[P1] Ticket escalado: No puede ingresar nadie"
    assert mensaje == "El ticket fue escalado. Motivo: Prioridad P1. SLA: 2026-10-05T15:03:11Z"


def test_asunto_y_mensaje_de_ticket_asignado():
    e = evento(
        "TicketAsignado", estado="ASIGNADO", prioridad="P2", agenteAsignadoId="b1c2d3e4-0000-4000-8000-000000000002"
    )
    asunto, mensaje = PlantillaFactory.para("TicketAsignado").generar(e)
    assert asunto == "Se te asignó el ticket: No puede ingresar nadie"
    assert "Prioridad: P2" in mensaje
    assert "SLA: 2026-10-05T15:03:11Z" in mensaje


def test_asunto_y_mensaje_de_ticket_resuelto():
    asunto, mensaje = PlantillaFactory.para("TicketResuelto").generar(evento("TicketResuelto", estado="RESUELTO"))
    assert asunto == "Tu ticket fue resuelto: No puede ingresar nadie"
    assert "7c9e6679-7425-40de-944b-e07fc1f90ae7" in mensaje


def test_sin_sla_ni_motivo():
    e = evento("TicketEscalado", fechaLimiteSla=None)
    e["payload"]["motivo"] = None
    _, mensaje = PlantillaFactory.para("TicketEscalado").generar(e)
    assert mensaje == "El ticket fue escalado. Motivo: sin motivo informado. SLA: sin definir"


def test_el_sla_sale_en_utc_sin_milisegundos():
    e = evento("TicketEscalado", fechaLimiteSla="2026-10-05T12:03:11.500-03:00")
    _, mensaje = PlantillaFactory.para("TicketEscalado").generar(e)
    assert mensaje.endswith("SLA: 2026-10-05T15:03:11Z")


def test_tipo_sin_plantilla():
    with pytest.raises(ValueError):
        PlantillaFactory.para("TicketCreado")
