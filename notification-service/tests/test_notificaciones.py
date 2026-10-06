from tests.conftest import sobre

AGENTE = "b1c2d3e4-0000-4000-8000-000000000002"


def _cargar_tres(client):
    client.post("/api/eventos", json=sobre("TicketEscalado"))
    client.post("/api/eventos", json=sobre("TicketAsignado", estado="ASIGNADO", agenteAsignadoId=AGENTE))
    client.post("/api/eventos", json=sobre("TicketResuelto", estado="RESUELTO", agenteAsignadoId=AGENTE))


def test_consulta_la_notificacion_registrada(client):
    evento = sobre("TicketEscalado")
    client.post("/api/eventos", json=evento)

    pagina = client.get("/api/notificaciones", params={"ticketId": evento["payload"]["ticket"]["ticketId"]}).json()

    assert pagina["totalElements"] == 1
    n = pagina["content"][0]
    assert set(n) == {
        "id", "eventId", "ticketId", "tipoEvento", "destinatario", "canal", "asunto", "mensaje", "estado", "fecha"
    }  # fmt: skip
    assert n["eventId"] == evento["eventId"]
    assert n["fecha"].endswith("Z") and "." not in n["fecha"]
    assert client.get(f"/api/notificaciones/{n['id']}").json() == n


def test_filtros_y_paginacion(client):
    _cargar_tres(client)

    primera = client.get("/api/notificaciones", params={"size": 2}).json()
    assert (primera["totalElements"], primera["totalPages"], len(primera["content"])) == (3, 2, 2)
    assert len(client.get("/api/notificaciones", params={"size": 2, "page": 1}).json()["content"]) == 1

    del_agente = client.get("/api/notificaciones", params={"destinatario": f"USUARIO:{AGENTE}"}).json()
    assert del_agente["totalElements"] == 1
    assert del_agente["content"][0]["canal"] == "INTERNA"


def test_sin_notificaciones_devuelve_una_pagina_vacia(client):
    assert client.get("/api/notificaciones").json() == {
        "content": [],
        "page": 0,
        "size": 20,
        "totalElements": 0,
        "totalPages": 0,
    }


def test_paginacion_invalida_responde_400(client):
    r = client.get("/api/notificaciones", params={"size": 101})
    assert r.status_code == 400
    assert r.json()["detalles"][0]["campo"] == "size"


def test_ticket_id_invalido_responde_400(client):
    r = client.get("/api/notificaciones", params={"ticketId": "no-es-uuid"})
    assert r.status_code == 400
    assert r.json()["detalles"][0]["campo"] == "ticketId"


def test_notificacion_inexistente_responde_404(client):
    for id in ("no-existe", "66f7c3b2e4b0a1b2c3d4e5f7"):
        r = client.get(f"/api/notificaciones/{id}")
        assert r.status_code == 404
        assert r.json()["codigo"] == "NO_ENCONTRADO"
