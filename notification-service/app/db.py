"""Cliente de Mongo. Nadie lo usa fuera de este archivo y de los repositories."""

from pymongo import MongoClient
from pymongo.database import Database

from app.config import settings

_client: MongoClient | None = None


def get_client() -> MongoClient:
    global _client
    if _client is None:
        _client = MongoClient(settings.mongo_uri, tz_aware=True, serverSelectionTimeoutMS=5000)
    return _client


def get_db() -> Database:
    return get_client()[settings.mongo_db]
