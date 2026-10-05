import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def client() -> TestClient:
    # raise_server_exceptions=False: queremos ver la respuesta 500 con el formato comun.
    return TestClient(create_app(), raise_server_exceptions=False)
