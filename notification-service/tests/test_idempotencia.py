import pytest

from app.providers.email_provider import EmailProvider
from app.repositories.notificacion_repository import NotificacionRepository
from app.services.evento_service import EventoService
from tests.conftest import evento


def test_mismo_evento_dos_veces_deja_una_sola_notificacion(mongo):
    servicio = EventoService(mongo)
    e = evento("TicketEscalado")

    assert servicio.procesar(e) == "PROCESADO"
    assert servicio.procesar(e) == "DUPLICADO"
    assert mongo.notificaciones.count_documents({}) == 1
    assert mongo.eventos_procesados.find_one({"eventId": e["eventId"]})["resultado"] == "PROCESADO"


def test_evento_ignorado_queda_marcado_y_el_repetido_es_duplicado(mongo):
    servicio = EventoService(mongo)
    e = evento("TicketCreado", estado="NUEVO")

    assert servicio.procesar(e) == "IGNORADO"
    assert servicio.procesar(e) == "DUPLICADO"
    assert mongo.notificaciones.count_documents({}) == 0


def test_si_falla_el_guardado_se_libera_la_marca_para_el_reintento(mongo, monkeypatch):
    e = evento("TicketEscalado")

    def falla(self, documento):
        raise RuntimeError("mongo caído")

    with monkeypatch.context() as m:
        m.setattr(NotificacionRepository, "insert", falla)
        with pytest.raises(RuntimeError):
            EventoService(mongo).procesar(e)
    assert mongo.eventos_procesados.count_documents({}) == 0

    assert EventoService(mongo).procesar(e) == "PROCESADO"
    assert mongo.notificaciones.count_documents({}) == 1


def test_si_falla_el_canal_la_notificacion_queda_fallida(mongo, monkeypatch):
    def falla(destinatario, asunto, mensaje):
        raise RuntimeError("smtp caído")

    monkeypatch.setattr(EmailProvider, "enviar", staticmethod(falla))

    assert EventoService(mongo).procesar(evento("TicketEscalado")) == "PROCESADO"
    assert mongo.notificaciones.find_one()["estado"] == "FALLIDA"
