import pytest

from app.application.masking import enmascarar


def test_caso_de_privacidad_del_contexto_no_deja_ningun_dato_original():
    texto = "Soy Juan, mi mail es juan.perez@empresa.com y mi celular 11-5555-1234, DNI 30.123.456"

    resultado = enmascarar(texto)

    assert resultado == "Soy Juan, mi mail es [EMAIL] y mi celular [TELEFONO], DNI [DNI]"
    for dato in ("juan.perez", "empresa.com", "5555", "1234", "30.123.456", "123.456"):
        assert dato not in resultado


@pytest.mark.parametrize("texto, esperado", [
    ("escribime a ana_maria+soporte@mail.empresa.com.ar", "escribime a [EMAIL]"),
    ("dos mails: a@b.co y c.d@e.org", "dos mails: [EMAIL] y [EMAIL]"),
])
def test_enmascara_emails(texto, esperado):
    assert enmascarar(texto) == esperado


@pytest.mark.parametrize("texto, esperado", [
    ("llamame al 11-5555-1234", "llamame al [TELEFONO]"),
    ("cel +54 9 11 5555-1234 gracias", "cel [TELEFONO] gracias"),
    ("tel 1155551234.", "tel [TELEFONO]."),
    ("fijo 4555 1234", "fijo [TELEFONO]"),
])
def test_enmascara_telefonos(texto, esperado):
    assert enmascarar(texto) == esperado


@pytest.mark.parametrize("texto, esperado", [
    ("DNI 30.123.456", "DNI [DNI]"),
    ("DNI 5.123.456", "DNI [DNI]"),
    ("mi documento es 30123456.", "mi documento es [DNI]."),
    ("DNI 5123456", "DNI [DNI]"),
])
def test_enmascara_dni(texto, esperado):
    assert enmascarar(texto) == esperado


@pytest.mark.parametrize("texto", [
    "La factura de septiembre del cliente 4411 salió con IVA duplicado",
    "Al confirmar el pago me da error 500",
    "El servidor está al 98% de disco",
    "Tardan más de 30 segundos desde esta mañana",
    "La factura salió por $1.500.000 y por $ 2.300.000",
    "Pasó el 2026-10-05 a las 9",
    "El monto es 1234567.50",
    "Producción caída, todos los usuarios bloqueados en el login desde las 9",
])
def test_no_toca_texto_sin_datos_personales(texto):
    assert enmascarar(texto) == texto
