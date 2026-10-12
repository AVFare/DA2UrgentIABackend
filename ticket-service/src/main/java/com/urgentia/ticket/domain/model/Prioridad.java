package com.urgentia.ticket.domain.model;

/**
 * Orden de atencion. P1 es lo mas urgente. La calcula el dominio a partir de
 * urgencia e impacto (nunca la IA) y define las horas del SLA.
 */
public enum Prioridad {
    P1(1),
    P2(4),
    P3(8),
    P4(24);

    private final int horasSla;

    Prioridad(int horasSla) {
        this.horasSla = horasSla;
    }

    /** Horas corridas (no habiles) que hay para resolver el ticket (seccion 8.2). */
    public int horasSla() {
        return horasSla;
    }
}
