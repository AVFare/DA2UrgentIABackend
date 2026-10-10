from app.services.canales import CANALES, obtener_canal

NOTIFICACION = {
    "destinatario": "GRUPO:GUARDIA",
    "asunto": "[P1] Ticket escalado: X",
    "mensaje": "El ticket fue escalado.",
}


def test_hay_una_estrategia_por_canal_del_contrato():
    assert set(CANALES) == {"EMAIL_SIMULADO", "INTERNA"}


def test_email_simulado_escribe_un_log_con_destinatario_asunto_y_mensaje(caplog):
    caplog.set_level("INFO")
    assert obtener_canal("EMAIL_SIMULADO").enviar(NOTIFICACION) == "ENVIADA"

    record = next(r for r in caplog.records if r.name == "providers.email")
    assert record.getMessage() == "EMAIL_SIMULADO"
    assert (record.destinatario, record.asunto, record.mensaje) == (
        "GRUPO:GUARDIA",
        "[P1] Ticket escalado: X",
        "El ticket fue escalado.",
    )


def test_canal_interno_solo_confirma(caplog):
    caplog.set_level("INFO")
    assert obtener_canal("INTERNA").enviar(NOTIFICACION) == "ENVIADA"
    assert caplog.records == []
