import json
import logging

from app.logging_config import JsonFormatter, configurar_logging, correlation_id_var


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "UP", "service": "notification-service", "version": "0.1.0"}


def test_genera_correlation_id_si_no_viene(client):
    assert client.get("/health").headers["X-Correlation-Id"]


def test_respeta_el_correlation_id_recibido(client):
    r = client.get("/health", headers={"X-Correlation-Id": "corr-123"})
    assert r.headers["X-Correlation-Id"] == "corr-123"


def test_ruta_inexistente_responde_con_el_formato_comun(client):
    r = client.get("/no-existe", headers={"X-Correlation-Id": "corr-404"})
    assert r.status_code == 404
    cuerpo = r.json()
    assert cuerpo["codigo"] == "NO_ENCONTRADO"
    assert cuerpo["path"] == "/no-existe"
    assert cuerpo["correlationId"] == "corr-404"
    assert cuerpo["timestamp"].endswith("Z")


def test_log_en_una_linea_json_con_correlation_id():
    configurar_logging()
    token = correlation_id_var.set("corr-log")
    try:
        record = logging.getLogger("prueba").makeRecord("prueba", logging.INFO, __file__, 1, "hola", None, None)
    finally:
        correlation_id_var.reset(token)
    record.destinatario = "GRUPO:GUARDIA"

    linea = json.loads(JsonFormatter().format(record))

    assert linea["level"] == "INFO"
    assert linea["service"] == "notification-service"
    assert linea["correlationId"] == "corr-log"
    assert linea["message"] == "hola"
    assert linea["destinatario"] == "GRUPO:GUARDIA"
    assert linea["timestamp"].endswith("Z")
