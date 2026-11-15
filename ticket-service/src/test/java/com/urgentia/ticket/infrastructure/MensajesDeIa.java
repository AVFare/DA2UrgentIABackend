package com.urgentia.ticket.infrastructure;

import java.util.UUID;

/**
 * ClasificacionGenerada como los publica el worker de classification-service
 * (contracts/events/clasificacion-events.schema.json). Los tests los validan contra el esquema.
 */
public final class MensajesDeIa {

    private MensajesDeIa() {
    }

    /** La respuesta de la demo: caida del login, critica. Con la matriz da P1 y escala. */
    public static String critica(UUID eventId, String ticketId, String correlationId) {
        return clasificado(eventId, ticketId, correlationId, "ALTA", "ALTO", true, 0.93);
    }

    /** Un caso comun: urgencia y impacto bajos, P4. */
    public static String comun(UUID eventId, String ticketId, String correlationId) {
        return clasificado(eventId, ticketId, correlationId, "BAJA", "BAJO", false, 0.81);
    }

    public static String clasificado(UUID eventId, String ticketId, String correlationId, String urgencia,
                                     String impacto, boolean requiereEscalamiento, double confianza) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "ClasificacionGenerada",
                  "version": 1,
                  "occurredAt": "2026-11-20T14:03:11Z",
                  "correlationId": "%s",
                  "source": "classification-service",
                  "payload": {
                    "ticketId": "%s",
                    "resultado": "CLASIFICADO",
                    "clasificacion": {
                      "id": "66f7c2a1e4b0a1b2c3d4e5f6",
                      "categoria": "INCIDENTE",
                      "urgencia": "%s",
                      "impacto": "%s",
                      "moduloAfectado": "AUTENTICACION",
                      "requiereEscalamiento": %s,
                      "confianza": %s,
                      "justificacion": "Caída total del login en producción",
                      "proveedor": "mock",
                      "modelo": "mock-v1",
                      "versionPrompt": "full",
                      "latenciaMs": 12,
                      "fecha": "2026-11-20T14:03:11Z"
                    }
                  }
                }
                """.formatted(eventId, correlationId, ticketId, urgencia, impacto, requiereEscalamiento, confianza);
    }

    public static String conError(UUID eventId, String ticketId, String correlationId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "ClasificacionGenerada",
                  "version": 1,
                  "occurredAt": "2026-11-20T14:03:16Z",
                  "correlationId": "%s",
                  "source": "classification-service",
                  "payload": {
                    "ticketId": "%s",
                    "resultado": "ERROR",
                    "error": { "codigo": "LLM_TIMEOUT", "mensaje": "El modelo no respondio en 5 s" }
                  }
                }
                """.formatted(eventId, correlationId, ticketId);
    }
}
