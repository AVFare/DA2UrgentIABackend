package com.urgentia.ticket.infrastructure;

/** Respuestas de classification-service para los tests (contrato de la seccion 9.3). */
public final class RespuestasIa {

    public static final String FECHA = "2026-10-05T14:03:11Z";

    private RespuestasIa() {
    }

    /** La del guion de la demo: INCIDENTE, ALTA, ALTO, AUTENTICACION, critico. Da P1 y escala. */
    public static String laDeLaDemo() {
        return clasificacion("INCIDENTE", "ALTA", "ALTO", "AUTENTICACION", true, 0.93);
    }

    /** Un caso comun: MEDIA y MEDIO dan P3 y no escala. */
    public static String comun() {
        return clasificacion("INCIDENTE", "MEDIA", "MEDIO", "PAGOS", false, 0.8);
    }

    /** BAJA y BAJO dan P4. */
    public static String consulta() {
        return clasificacion("CONSULTA", "BAJA", "BAJO", "REPORTES", false, 0.9);
    }

    public static String clasificacion(String categoria, String urgencia, String impacto, String modulo,
                                       boolean escalar, double confianza) {
        return """
                {"id":"66f7c2a1e4b0a1b2c3d4e5f6","categoria":"%s","urgencia":"%s","impacto":"%s",\
                "moduloAfectado":"%s","requiereEscalamiento":%s,"confianza":%s,\
                "justificacion":"Respuesta de prueba","proveedor":"mock","modelo":"mock-v1",\
                "versionPrompt":"v1","latenciaMs":12,"fecha":"%s"}"""
                .formatted(categoria, urgencia, impacto, modulo, escalar, confianza, FECHA);
    }
}
