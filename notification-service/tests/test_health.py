def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "UP", "service": "notification-service", "version": "0.1.0"}
