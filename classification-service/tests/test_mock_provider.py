import asyncio
import json

import pytest

from app.application.ports.llm_provider import PedidoLlm
from app.domain.models import Clasificacion
from app.infrastructure.llm.mock_provider import MockLlmProvider, clasificar_por_reglas, normalizar


def test_normaliza_a_minusculas_y_sin_tildes():
    assert normalizar("Producción CAÍDA, contraseña y compañeros") == "produccion caida, contrasena y companeros"


@pytest.mark.parametrize("titulo, descripcion, categoria, urgencia, impacto, escalar", [
    # 1. caida/no funciona/lento + produccion/todos/nadie/toda la empresa
    ("No puede ingresar nadie", "Producción caída, todos los usuarios bloqueados en el login desde las 9",
     "INCIDENTE", "ALTA", "ALTO", True),
    ("Sistema muy lento", "Todo anda lento y afecta a toda la empresa", "INCIDENTE", "ALTA", "ALTO", True),
    ("No funciona el ERP", "No funciona para nadie desde esta mañana", "INCIDENTE", "ALTA", "ALTO", True),
    # 2. error/no puedo/falla (impacto MEDIO si menciona a otros)
    ("Error al pagar", "Me da error 500 al confirmar", "INCIDENTE", "MEDIA", "BAJO", False),
    ("No puedo pagar", "A otros compañeros del área también les pasa", "INCIDENTE", "MEDIA", "MEDIO", False),
    ("Falla la impresora", "Falla desde ayer", "INCIDENTE", "MEDIA", "BAJO", False),
    # 3. bug/no hace nada/salio mal/incorrect
    ("Botón de reporte", "El botón Descargar PDF no hace nada en Firefox", "BUG", "MEDIA", "BAJO", False),
    ("Factura", "La factura salió con un monto incorrecto", "BUG", "MEDIA", "BAJO", False),
    # 4. necesito/solicito/alta de/dar de alta
    ("Alta de usuario", "Necesito que den de alta a un empleado nuevo", "SOLICITUD", "BAJA", "BAJO", False),
    # 5. como/consulta/?
    ("Consulta sobre reportes", "¿Cómo exporto el reporte mensual a Excel?", "CONSULTA", "BAJA", "BAJO", False),
    ("Duda", "Se puede cambiar el idioma?", "CONSULTA", "BAJA", "BAJO", False),
    # 6. ninguna regla
    ("Hola", "Quería dejar un comentario general sobre el sistema", "CONSULTA", "BAJA", "BAJO", False),
])
def test_reglas_de_categoria_urgencia_impacto_y_escalamiento(titulo, descripcion, categoria, urgencia, impacto,
                                                              escalar):
    resultado = clasificar_por_reglas(titulo, descripcion)

    assert (resultado["categoria"], resultado["urgencia"], resultado["impacto"],
            resultado["requiereEscalamiento"]) == (categoria, urgencia, impacto, escalar)


def test_las_reglas_se_evaluan_en_orden():
    # Dice "error" y tambien "caida ... todos": gana la regla 1.
    resultado = clasificar_por_reglas("Error grave", "Caída total, todos los usuarios afectados")

    assert resultado["categoria"] == "INCIDENTE"
    assert resultado["urgencia"] == "ALTA"


@pytest.mark.parametrize("texto, modulo", [
    ("No puedo hacer login", "AUTENTICACION"),
    ("Olvidé mi contraseña", "AUTENTICACION"),
    ("La factura salió mal", "FACTURACION"),
    ("Rechazan los pagos con tarjeta", "PAGOS"),
    ("El reporte en Excel sale vacío", "REPORTES"),
    ("El servidor se quedó sin disco", "INFRAESTRUCTURA"),
    ("La base de datos responde lento", "BASE_DE_DATOS"),
    ("Las consultas tardan mucho", "BASE_DE_DATOS"),
    ("La sincronización con el banco falló", "INTEGRACIONES"),
    ("La impresora no imprime", "OTRO"),
])
def test_modulo_por_palabra_clave(texto, modulo):
    assert clasificar_por_reglas("Ticket", texto)["moduloAfectado"] == modulo


def test_busca_palabras_al_inicio_y_no_en_el_medio():
    # "red" no tiene que encontrarse dentro de "crédito".
    assert clasificar_por_reglas("Ticket", "Pregunta sobre el límite de crédito")["moduloAfectado"] == "OTRO"


def test_confianza_fija_y_justificacion_con_la_regla():
    resultado = clasificar_por_reglas("Alta de usuario", "Necesito un usuario nuevo")

    assert resultado["confianza"] == 0.7
    assert resultado["justificacion"] == "Regla del mock: pedido de algo nuevo"


def test_el_provider_devuelve_json_que_cumple_el_dominio():
    pedido = PedidoLlm(
        titulo="No puede ingresar nadie",
        descripcion="Producción caída, todos los usuarios bloqueados en el login",
        prompt="(el mock no usa el prompt)",
    )

    respuesta = asyncio.run(MockLlmProvider().consultar(pedido))

    assert respuesta.modelo == "mock-v1"
    datos = json.loads(respuesta.texto)
    clasificacion = Clasificacion(
        categoria=datos["categoria"],
        urgencia=datos["urgencia"],
        impacto=datos["impacto"],
        modulo_afectado=datos["moduloAfectado"],
        requiere_escalamiento=datos["requiereEscalamiento"],
        confianza=datos["confianza"],
        justificacion=datos["justificacion"],
    )
    assert clasificacion.requiere_escalamiento is True
    assert MockLlmProvider.nombre == "mock"
