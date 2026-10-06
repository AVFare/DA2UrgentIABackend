"""Test de integracion del repositorio contra un MongoDB real.

Se corre solo si esta definida MONGO_URI_TEST, por ejemplo con el Mongo del Compose:
    MONGO_URI_TEST="mongodb://root:mongo@localhost:27017/?authSource=admin" pytest tests/test_mongo_repository.py
Usa la base classification_test y la borra al terminar.
"""

import asyncio
import os
from datetime import datetime, timedelta, timezone
from uuid import UUID

import pytest
from pymongo import AsyncMongoClient

from app.domain.models import RegistroClasificacion
from app.infrastructure.persistence.clasificacion_repository import COLECCION, MongoClasificacionRepository
from tests.fakes import CLASIFICACION_CRITICA

MONGO_URI_TEST = os.getenv("MONGO_URI_TEST")
BASE_DE_TEST = "classification_test"
TICKET_A = UUID("7c9e6679-7425-40de-944b-e07fc1f90ae7")
TICKET_B = UUID("11111111-1111-4111-8111-111111111111")
INICIO = datetime(2026, 10, 5, 14, 0, 0, tzinfo=timezone.utc)

pytestmark = pytest.mark.skipif(not MONGO_URI_TEST, reason="Definir MONGO_URI_TEST para correr contra MongoDB")


def registro(ticket_id: UUID, minutos: int) -> RegistroClasificacion:
    return RegistroClasificacion(
        ticket_id=ticket_id,
        clasificacion=CLASIFICACION_CRITICA,
        proveedor="mock",
        modelo="mock-v1",
        version_prompt="v1",
        latencia_ms=812,
        fecha=INICIO + timedelta(minutes=minutos),
        texto_enmascarado="Login\nMi mail es [EMAIL]",
        respuesta_cruda='{"categoria": "INCIDENTE"}',
    )


def con_repositorio(prueba):
    async def correr():
        cliente = AsyncMongoClient(MONGO_URI_TEST, tz_aware=True, serverSelectionTimeoutMS=3000)
        try:
            await cliente.drop_database(BASE_DE_TEST)
            repositorio = MongoClasificacionRepository(cliente[BASE_DE_TEST][COLECCION])
            await repositorio.crear_indices()
            await prueba(repositorio, cliente[BASE_DE_TEST][COLECCION])
        finally:
            await cliente.drop_database(BASE_DE_TEST)
            await cliente.close()

    asyncio.run(correr())


def test_guarda_y_lee_el_registro_completo():
    async def prueba(repositorio, coleccion):
        original = registro(TICKET_A, 0)

        guardado = await repositorio.guardar(original)
        pagina = await repositorio.listar(TICKET_A, 0, 20)

        assert guardado.id is not None
        assert pagina.elementos == [guardado]
        documento = await coleccion.find_one()
        assert documento["ticketId"] == str(TICKET_A)
        assert documento["moduloAfectado"] == "AUTENTICACION"
        assert documento["textoEnmascarado"] == "Login\nMi mail es [EMAIL]"
        assert documento["respuestaCruda"] == '{"categoria": "INCIDENTE"}'

    con_repositorio(prueba)


def test_lista_mas_reciente_primero_filtrado_y_paginado():
    async def prueba(repositorio, _):
        for minutos in (0, 10, 5):
            await repositorio.guardar(registro(TICKET_A, minutos))
        await repositorio.guardar(registro(TICKET_B, 20))

        primera = await repositorio.listar(TICKET_A, 0, 2)
        segunda = await repositorio.listar(TICKET_A, 1, 2)
        todas = await repositorio.listar(None, 0, 20)

        assert [r.fecha.minute for r in primera.elementos] == [10, 5]
        assert [r.fecha.minute for r in segunda.elementos] == [0]
        assert (primera.total, primera.total_pages) == (3, 2)
        assert todas.total == 4
        assert todas.elementos[0].ticket_id == TICKET_B

    con_repositorio(prueba)
