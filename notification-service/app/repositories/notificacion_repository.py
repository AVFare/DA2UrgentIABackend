from pymongo import ASCENDING, DESCENDING

from app.repositories.mongo_repository import MongoRepository


class NotificacionRepository(MongoRepository):
    """Coleccion notificaciones: { _id, eventId, ticketId, tipoEvento, destinatario, canal,
    asunto, mensaje, estado, fecha }."""

    collection_name = "notificaciones"

    def ensure_indexes(self) -> None:
        self.collection.create_index([("ticketId", ASCENDING), ("fecha", DESCENDING)])
        self.collection.create_index([("destinatario", ASCENDING), ("fecha", DESCENDING)])
        # Segunda red contra duplicados, ademas de eventos_procesados.
        self.collection.create_index([("eventId", ASCENDING), ("destinatario", ASCENDING)], unique=True)
