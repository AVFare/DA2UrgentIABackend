from datetime import UTC, datetime

from pymongo import ASCENDING
from pymongo.errors import DuplicateKeyError

from app.repositories.mongo_repository import MongoRepository


class EventoProcesadoRepository(MongoRepository):
    """Coleccion eventos_procesados: { _id, eventId (unico), eventType, resultado, fechaProcesado }."""

    collection_name = "eventos_procesados"

    def ensure_indexes(self) -> None:
        self.collection.create_index([("eventId", ASCENDING)], unique=True)

    def registrar(self, event_id: str, event_type: str) -> bool:
        """Marca el evento como recibido. Devuelve False si ya estaba.

        Se inserta directo y se confia en el indice unico: "buscar y despues insertar"
        dejaria pasar dos eventos iguales que llegan a la vez.
        """
        try:
            self.collection.insert_one(
                {"eventId": event_id, "eventType": event_type, "fechaProcesado": datetime.now(UTC)}
            )
        except DuplicateKeyError:
            return False
        return True

    def actualizar_resultado(self, event_id: str, resultado: str) -> None:
        self.collection.update_one({"eventId": event_id}, {"$set": {"resultado": resultado}})

    def eliminar(self, event_id: str) -> None:
        self.collection.delete_one({"eventId": event_id})
