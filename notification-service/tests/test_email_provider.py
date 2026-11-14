import smtplib

import pytest
from pydantic import SecretStr, ValidationError

from app.config import Settings, settings
from app.providers.email_provider import EmailProvider
from app.services.evento_service import EventoService
from tests.conftest import evento


class SmtpFalso:
    """Reemplaza a smtplib.SMTP: guarda lo que se hubiera mandado."""

    enviados: list = []
    conexiones: list = []
    falla: Exception | None = None

    def __init__(self, host, port, timeout):
        SmtpFalso.conexiones.append((host, port, timeout))
        self.tls = False

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def starttls(self):
        self.tls = True

    def login(self, usuario, clave):
        self.login_hecho = (usuario, clave)

    def send_message(self, correo):
        if SmtpFalso.falla:
            raise SmtpFalso.falla
        assert self.tls, "tiene que mandar por TLS"
        SmtpFalso.enviados.append((correo, self.login_hecho))


@pytest.fixture
def smtp(monkeypatch):
    SmtpFalso.enviados, SmtpFalso.conexiones, SmtpFalso.falla = [], [], None
    monkeypatch.setattr(smtplib, "SMTP", SmtpFalso)
    return SmtpFalso


@pytest.fixture
def mail_real(monkeypatch):
    monkeypatch.setattr(settings, "email_provider", "real")
    monkeypatch.setattr(settings, "smtp_user", "urgentia.demo@gmail.com")
    monkeypatch.setattr(settings, "smtp_password", SecretStr("abcd efgh ijkl mnop"))
    monkeypatch.setattr(settings, "email_destino_demo", "demo@example.com")
    monkeypatch.setattr(settings, "email_remitente", None)


def test_por_defecto_es_simulado_y_no_abre_conexiones(smtp, caplog):
    caplog.set_level("INFO")
    assert settings.email_provider == "simulado"

    assert EmailProvider.enviar("GRUPO:GUARDIA", "Asunto", "Mensaje") is True

    assert smtp.conexiones == []
    assert any(r.getMessage() == "EMAIL_SIMULADO" for r in caplog.records)


def test_real_manda_por_smtp_con_tls_a_la_casilla_de_demo(smtp, mail_real, caplog):
    caplog.set_level("INFO")

    assert EmailProvider.enviar("GRUPO:GUARDIA", "[P1] Ticket escalado: X", "El ticket fue escalado.") is True

    assert smtp.conexiones == [("smtp.gmail.com", 587, 10)]
    correo, (usuario, clave) = smtp.enviados[0]
    assert correo["To"] == "demo@example.com"
    assert correo["From"] == "urgentia.demo@gmail.com"
    assert correo["Subject"] == "[GRUPO:GUARDIA] [P1] Ticket escalado: X"
    assert "Destinatario original: GRUPO:GUARDIA" in correo.get_content()
    assert (usuario, clave) == ("urgentia.demo@gmail.com", "abcdefghijklmnop")
    envio = next(r for r in caplog.records if r.getMessage() == "EMAIL_ENVIADO")
    assert "abcd" not in str(envio.__dict__)


def test_si_gmail_falla_la_notificacion_queda_fallida_y_el_evento_no_se_pierde(smtp, mail_real, mongo):
    smtp.falla = smtplib.SMTPAuthenticationError(535, b"Username and Password not accepted")
    e = evento("TicketEscalado")

    assert EventoService(mongo).procesar(e) == "PROCESADO"

    assert mongo.notificaciones.find_one()["estado"] == "FALLIDA"
    assert mongo.eventos_procesados.find_one({"eventId": e["eventId"]})["resultado"] == "PROCESADO"


def test_real_sin_credenciales_no_arranca(monkeypatch):
    for variable in ("SMTP_USER", "SMTP_PASSWORD", "EMAIL_DESTINO_DEMO"):
        monkeypatch.delenv(variable, raising=False)
    with pytest.raises(ValidationError, match="SMTP_USER, SMTP_PASSWORD, EMAIL_DESTINO_DEMO"):
        Settings(email_provider="real", _env_file=None)


def test_proveedor_desconocido_no_arranca():
    with pytest.raises(ValidationError):
        Settings(email_provider="sendgrid", _env_file=None)


def test_real_con_variables_vacias_del_compose_no_arranca():
    with pytest.raises(ValidationError, match="SMTP_PASSWORD"):
        Settings(
            email_provider="real",
            smtp_user="urgentia.demo@gmail.com",
            smtp_password="",
            email_destino_demo="demo@example.com",
            _env_file=None,
        )
