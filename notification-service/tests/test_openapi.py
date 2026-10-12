def _esquema(client) -> dict:
    return client.get("/openapi.json").json()


def test_declara_el_esquema_bearer_para_el_boton_authorize(client):
    esquema = _esquema(client)
    assert esquema["components"]["securitySchemes"]["bearer"] == {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT",
        "description": "Token de POST /api/auth/login (user-service). Lo valida el gateway.",
    }
    assert esquema["security"] == [{"bearer": []}]


def test_las_consultas_piden_token_y_health_y_eventos_no(client):
    paths = _esquema(client)["paths"]
    assert "security" not in paths["/api/notificaciones"]["get"]
    assert "security" not in paths["/api/notificaciones/{id}"]["get"]
    assert paths["/health"]["get"]["security"] == []
    assert paths["/api/eventos"]["post"]["security"] == []


def test_el_primer_servidor_es_el_gateway(client):
    assert _esquema(client)["servers"][0]["url"] == "http://localhost:8080"


def test_no_documenta_el_422_de_fastapi(client):
    esquema = _esquema(client)
    for operaciones in esquema["paths"].values():
        for operacion in operaciones.values():
            assert "422" not in operacion["responses"]
    assert "HTTPValidationError" not in esquema["components"]["schemas"]
