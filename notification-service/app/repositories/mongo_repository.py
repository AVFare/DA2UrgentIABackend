"""Logica comun de los repositories. No se modifica sin consenso: afecta a todo el servicio."""

from typing import Any

from bson import ObjectId
from bson.errors import InvalidId
from pymongo.database import Database


class MongoRepository:
    collection_name: str

    def __init__(self, db: Database):
        self.collection = db[self.collection_name]

    def ensure_indexes(self) -> None:
        """Crea los indices de la coleccion. Se llama al arrancar."""

    def insert(self, documento: dict[str, Any]) -> dict[str, Any]:
        documento = dict(documento)
        resultado = self.collection.insert_one(documento)
        documento["_id"] = resultado.inserted_id
        return self.to_dict(documento)

    def find_by_id(self, id: str) -> dict[str, Any] | None:
        try:
            oid = ObjectId(id)
        except (InvalidId, TypeError):
            return None
        documento = self.collection.find_one({"_id": oid})
        return self.to_dict(documento) if documento else None

    def find_paginated(
        self, filtros: dict[str, Any], page: int, size: int, sort: list[tuple[str, int]]
    ) -> tuple[list[dict[str, Any]], int]:
        total = self.collection.count_documents(filtros)
        cursor = self.collection.find(filtros).sort(sort).skip(page * size).limit(size)
        return [self.to_dict(d) for d in cursor], total

    @staticmethod
    def to_dict(documento: dict[str, Any]) -> dict[str, Any]:
        """Convierte el _id (ObjectId) en id (string)."""
        resultado = dict(documento)
        resultado["id"] = str(resultado.pop("_id"))
        return resultado
