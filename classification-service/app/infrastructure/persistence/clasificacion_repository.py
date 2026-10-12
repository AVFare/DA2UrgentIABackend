"""Repositorio de clasificaciones sobre MongoDB (coleccion clasificaciones de classification_db).

Cada documento tiene los campos de la respuesta de la API mas respuestaCruda y
textoEnmascarado (CONTEXTO_PROYECTO.md, 9.3).
"""

from dataclasses import replace
from uuid import UUID

from pymongo import ASCENDING, DESCENDING
from pymongo.asynchronous.collection import AsyncCollection

from app.application.ports.clasificacion_repository import Pagina
from app.domain.models import Clasificacion, RegistroClasificacion

COLECCION = "clasificaciones"


class MongoClasificacionRepository:
    def __init__(self, coleccion: AsyncCollection) -> None:
        self._coleccion = coleccion

    async def crear_indices(self) -> None:
        await self._coleccion.create_index([("ticketId", ASCENDING), ("fecha", DESCENDING)])
        await self._coleccion.create_index([("fecha", DESCENDING)])

    async def guardar(self, registro: RegistroClasificacion) -> RegistroClasificacion:
        resultado = await self._coleccion.insert_one(_a_documento(registro))
        return replace(registro, id=str(resultado.inserted_id))

    async def listar(self, ticket_id: UUID | None, page: int, size: int) -> Pagina[RegistroClasificacion]:
        filtro = {"ticketId": str(ticket_id)} if ticket_id else {}
        total = await self._coleccion.count_documents(filtro)
        cursor = (
            self._coleccion.find(filtro)
            .sort([("fecha", DESCENDING), ("_id", DESCENDING)])
            .skip(page * size)
            .limit(size)
        )
        documentos = await cursor.to_list()
        return Pagina([_a_registro(documento) for documento in documentos], page, size, total)


def _a_documento(registro: RegistroClasificacion) -> dict:
    clasificacion = registro.clasificacion
    return {
        "ticketId": str(registro.ticket_id),
        "categoria": clasificacion.categoria.value,
        "urgencia": clasificacion.urgencia.value,
        "impacto": clasificacion.impacto.value,
        "moduloAfectado": clasificacion.modulo_afectado.value,
        "requiereEscalamiento": clasificacion.requiere_escalamiento,
        "confianza": clasificacion.confianza,
        "justificacion": clasificacion.justificacion,
        "proveedor": registro.proveedor,
        "modelo": registro.modelo,
        "versionPrompt": registro.version_prompt,
        "latenciaMs": registro.latencia_ms,
        "fecha": registro.fecha,
        "respuestaCruda": registro.respuesta_cruda,
        "textoEnmascarado": registro.texto_enmascarado,
    }


def _a_registro(documento: dict) -> RegistroClasificacion:
    return RegistroClasificacion(
        id=str(documento["_id"]),
        ticket_id=UUID(documento["ticketId"]),
        clasificacion=Clasificacion(
            categoria=documento["categoria"],
            urgencia=documento["urgencia"],
            impacto=documento["impacto"],
            modulo_afectado=documento["moduloAfectado"],
            requiere_escalamiento=documento["requiereEscalamiento"],
            confianza=documento["confianza"],
            justificacion=documento["justificacion"],
        ),
        proveedor=documento["proveedor"],
        modelo=documento["modelo"],
        version_prompt=documento["versionPrompt"],
        latencia_ms=documento["latenciaMs"],
        fecha=documento["fecha"],
        texto_enmascarado=documento["textoEnmascarado"],
        respuesta_cruda=documento["respuestaCruda"],
    )
