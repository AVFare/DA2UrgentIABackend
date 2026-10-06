import json

from app.logging_config import JsonFormatter
from app.repositories.notificacion_repository import NotificacionRepository
from tests.conftest import sobre


def test_escalado_registra_una_notificacion_a_la_guardia(client, mongo, caplog):
    caplog.set_level("INFO")
    evento = sobre("TicketEscalado")

    r = client.post("/api/eventos", json=evento)

    assert r.status_code == 202
    assert r.json() == {"eventId": evento["eventId"], "resultado": "PROCESADO"}
    guardada = mongo.notificaciones.find_one()
    assert guardada["destinatario"] == "GRUPO:GUARDIA"
    assert guardada["canal"] == "EMAIL_SIMULADO"
    assert guardada["estado"] == "ENVIADA"
    assert guardada["asunto"] == "[P1] Ticket escalado: No puede ingresar nadie"

    # El email simulado se loguea con el correlationId del sobre, no con el del request.
    log = next(json.loads(JsonFormatter().format(r)) for r in caplog.records if r.name == "providers.email")
    assert log["correlationId"] == evento["correlationId"]
    assert log["destinatario"] == "GRUPO:GUARDIA"


def test_evento_repetido_responde_duplicado(client, mongo):
    evento = sobre("TicketEscalado")
    client.post("/api/eventos", json=evento)

    r = client.post("/api/eventos", json=evento)

    assert r.status_code == 202
    assert r.json()["resultado"] == "DUPLICADO"
    assert mongo.notificaciones.count_documents({}) == 1


def test_evento_que_no_notifica_responde_ignorado(client, mongo):
    r = client.post("/api/eventos", json=sobre("TicketClasificado", estado="CLASIFICADO"))
    assert r.status_code == 202
    assert r.json()["resultado"] == "IGNORADO"
    assert mongo.notificaciones.count_documents({}) == 0


def test_sobre_invalido_responde_400_con_formato_comun(client):
    evento = sobre("TicketEscalado")
    del evento["payload"]["ticket"]["titulo"]
    evento["eventType"] = "TicketInventado"

    r = client.post("/api/eventos", json=evento, headers={"X-Correlation-Id": "corr-400"})

    assert r.status_code == 400
    cuerpo = r.json()
    assert cuerpo["codigo"] == "VALIDACION"
    assert cuerpo["path"] == "/api/eventos"
    assert cuerpo["correlationId"] == "corr-400"
    assert {"eventType", "payload.ticket.titulo"} <= {d["campo"] for d in cuerpo["detalles"]}


def test_asignado_sin_agente_responde_400_y_no_marca_el_evento(client, mongo):
    evento = sobre("TicketAsignado", estado="ASIGNADO", agenteAsignadoId=None)

    r = client.post("/api/eventos", json=evento)

    assert r.status_code == 400
    assert r.json()["detalles"][0]["campo"] == "payload.ticket.agenteAsignadoId"
    assert mongo.eventos_procesados.count_documents({}) == 0


def test_error_no_controlado_responde_500_sin_stacktrace_y_libera_el_evento(client, mongo, monkeypatch):
    def falla(self, documento):
        raise RuntimeError("mongo caído")

    monkeypatch.setattr(NotificacionRepository, "insert", falla)
    evento = sobre("TicketEscalado")

    r = client.post("/api/eventos", json=evento, headers={"X-Correlation-Id": "corr-500"})

    assert r.status_code == 500
    assert r.json()["codigo"] == "ERROR_INTERNO"
    assert r.json()["correlationId"] == "corr-500"
    assert "Traceback" not in r.text and "mongo caído" not in r.text
    assert mongo.eventos_procesados.count_documents({}) == 0


def test_swagger_documenta_400_y_no_422(client):
    esquema = client.get("/openapi.json").json()
    respuestas = esquema["paths"]["/api/eventos"]["post"]["responses"]
    assert "400" in respuestas and "422" not in respuestas
    assert "HTTPValidationError" not in esquema["components"]["schemas"]
