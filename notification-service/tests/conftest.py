import copy
import uuid

import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.schemas.evento import EJEMPLO_SOBRE, InputEvento


@pytest.fixture
def client():
    with TestClient(create_app(), raise_server_exceptions=False) as c:
        yield c


def sobre(event_type: str = "TicketEscalado", **ticket) -> dict:
    """Sobre del contrato con un eventId nuevo; los kwargs pisan campos de payload.ticket."""
    datos = copy.deepcopy(EJEMPLO_SOBRE)
    datos["eventId"] = str(uuid.uuid4())
    datos["eventType"] = event_type
    datos["payload"]["ticket"].update(ticket)
    return datos


def evento(event_type: str = "TicketEscalado", **ticket) -> dict:
    """El dict que el endpoint le pasa a los services."""
    return InputEvento.model_validate(sobre(event_type, **ticket)).model_dump(mode="json", by_alias=True)
