import copy
import uuid

import mongomock
import pytest
from fastapi.testclient import TestClient

from app import db as db_module
from app.config import settings
from app.main import create_app
from app.repositories.evento_procesado_repository import EventoProcesadoRepository
from app.repositories.notificacion_repository import NotificacionRepository
from app.schemas.evento import EJEMPLO_SOBRE, InputEvento


@pytest.fixture
def mongo(monkeypatch):
    """Base de test en memoria: reemplaza el cliente real de Mongo y crea los indices como al arrancar."""
    cliente = mongomock.MongoClient(tz_aware=True)
    monkeypatch.setattr(db_module, "_client", cliente)
    db = cliente[settings.mongo_db]
    NotificacionRepository(db).ensure_indexes()
    EventoProcesadoRepository(db).ensure_indexes()
    return db


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
