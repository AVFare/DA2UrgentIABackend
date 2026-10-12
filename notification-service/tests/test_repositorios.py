from datetime import UTC, datetime

import pytest
from pymongo.errors import DuplicateKeyError

from app.repositories.evento_procesado_repository import EventoProcesadoRepository
from app.repositories.notificacion_repository import NotificacionRepository

EVENT_ID = "3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"


def test_registrar_un_evento_repetido_devuelve_false(mongo):
    repo = EventoProcesadoRepository(mongo)
    assert repo.registrar(EVENT_ID, "TicketEscalado") is True
    assert repo.registrar(EVENT_ID, "TicketEscalado") is False
    assert mongo.eventos_procesados.count_documents({}) == 1


def test_actualizar_resultado_y_eliminar(mongo):
    repo = EventoProcesadoRepository(mongo)
    repo.registrar(EVENT_ID, "TicketEscalado")

    repo.actualizar_resultado(EVENT_ID, "PROCESADO")
    assert mongo.eventos_procesados.find_one({"eventId": EVENT_ID})["resultado"] == "PROCESADO"

    repo.eliminar(EVENT_ID)
    assert repo.registrar(EVENT_ID, "TicketEscalado") is True


def test_insert_devuelve_el_documento_con_id_string(mongo):
    repo = NotificacionRepository(mongo)
    guardada = repo.insert({"eventId": EVENT_ID, "destinatario": "GRUPO:GUARDIA", "fecha": datetime.now(UTC)})

    assert isinstance(guardada["id"], str) and "_id" not in guardada
    assert repo.find_by_id(guardada["id"])["destinatario"] == "GRUPO:GUARDIA"


def test_find_by_id_con_id_invalido_o_inexistente(mongo):
    repo = NotificacionRepository(mongo)
    assert repo.find_by_id("no-es-objectid") is None
    assert repo.find_by_id("66f7c3b2e4b0a1b2c3d4e5f7") is None


def test_no_se_repite_la_notificacion_de_un_evento_al_mismo_destinatario(mongo):
    repo = NotificacionRepository(mongo)
    repo.insert({"eventId": EVENT_ID, "destinatario": "GRUPO:GUARDIA"})
    with pytest.raises(DuplicateKeyError):
        repo.insert({"eventId": EVENT_ID, "destinatario": "GRUPO:GUARDIA"})
