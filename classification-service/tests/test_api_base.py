"""Tests de la base de la API: health, correlationId, formato comun de error y OpenAPI."""

PEDIDO_VALIDO = {
    "ticketId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "titulo": "No puede ingresar nadie",
    "descripcion": "Producción caída, todos los usuarios bloqueados en el login desde las 9",
}


def test_health_responde_up(client):
    respuesta = client.get("/health")

    assert respuesta.status_code == 200
    assert respuesta.json() == {"status": "UP", "service": "classification-service", "version": "0.1.0"}


def test_reusa_el_correlation_id_que_llega(client):
    respuesta = client.get("/health", headers={"X-Correlation-Id": "abc-123"})

    assert respuesta.headers["X-Correlation-Id"] == "abc-123"


def test_genera_correlation_id_si_no_llega(client):
    respuesta = client.get("/health")

    assert respuesta.headers["X-Correlation-Id"]


def test_body_invalido_responde_400_validacion_con_detalles(client):
    pedido = {**PEDIDO_VALIDO, "titulo": "abc", "ticketId": "no-es-uuid"}

    respuesta = client.post("/api/clasificaciones", json=pedido, headers={"X-Correlation-Id": "abc-123"})

    assert respuesta.status_code == 400
    error = respuesta.json()
    assert error["codigo"] == "VALIDACION"
    assert error["path"] == "/api/clasificaciones"
    assert error["correlationId"] == "abc-123"
    assert error["timestamp"].endswith("Z")
    assert {"campo": "titulo", "mensaje": "debe tener al menos 5 caracteres"} in error["detalles"]
    assert {"campo": "ticketId", "mensaje": "tiene que ser un UUID"} in error["detalles"]


def test_falta_un_campo_responde_400(client):
    pedido = {k: v for k, v in PEDIDO_VALIDO.items() if k != "descripcion"}

    respuesta = client.post("/api/clasificaciones", json=pedido)

    assert respuesta.status_code == 400
    assert {"campo": "descripcion", "mensaje": "es obligatorio"} in respuesta.json()["detalles"]


def test_get_con_ticket_id_invalido_responde_400(client):
    respuesta = client.get("/api/clasificaciones", params={"ticketId": "no-es-uuid"})

    assert respuesta.status_code == 400
    assert respuesta.json()["detalles"] == [{"campo": "ticketId", "mensaje": "tiene que ser un UUID"}]


def test_get_con_paginacion_fuera_de_rango_responde_400(client):
    respuesta = client.get("/api/clasificaciones", params={"page": -1, "size": 101})

    assert respuesta.status_code == 400
    campos = {detalle["campo"] for detalle in respuesta.json()["detalles"]}
    assert campos == {"page", "size"}


def test_ruta_inexistente_responde_404_no_encontrado(client):
    respuesta = client.get("/api/otra-cosa")

    assert respuesta.status_code == 404
    assert respuesta.json()["codigo"] == "NO_ENCONTRADO"


def test_error_inesperado_responde_500_sin_stacktrace(client):
    # Mientras no este la Facade, clasificar lanza NotImplementedError.
    respuesta = client.post("/api/clasificaciones", json=PEDIDO_VALIDO)

    assert respuesta.status_code == 500
    error = respuesta.json()
    assert error["codigo"] == "ERROR_INTERNO"
    assert "Traceback" not in respuesta.text


def test_openapi_respeta_las_convenciones(client):
    openapi = client.get("/openapi.json").json()

    clasificar = openapi["paths"]["/api/clasificaciones"]["post"]
    assert set(clasificar["responses"]) == {"200", "400", "500", "502", "504"}
    assert "HTTPValidationError" not in openapi["components"]["schemas"]
    assert openapi["components"]["securitySchemes"]["bearer"]["scheme"] == "bearer"
    assert openapi["paths"]["/health"]["get"]["security"] == []
    listar = openapi["paths"]["/api/clasificaciones"]["get"]
    filtros = {p["name"]: p["required"] for p in listar["parameters"] if p["in"] == "query"}
    assert filtros == {"ticketId": False, "page": False, "size": False}
    assert set(openapi["components"]["schemas"]["PaginaClasificaciones"]["properties"]) == {
        "content", "page", "size", "totalElements", "totalPages"}
    respuesta = openapi["components"]["schemas"]["ClasificacionResponse"]["properties"]
    assert {"ticketId", "moduloAfectado", "requiereEscalamiento", "versionPrompt", "latenciaMs"} <= set(respuesta)
    assert "prioridad" not in respuesta
