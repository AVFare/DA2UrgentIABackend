package com.urgentia.ticket.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Tiempo maximo de resolucion (Value Object). Se calcula desde la fecha de
 * clasificacion sumando las horas que corresponden a la prioridad (seccion 8.2).
 */
public record Sla(Prioridad prioridad, Instant fechaLimite) {

    public Sla {
        Objects.requireNonNull(prioridad, "La prioridad del SLA es obligatoria");
        Objects.requireNonNull(fechaLimite, "La fecha limite del SLA es obligatoria");
    }

    public static Sla calcular(Prioridad prioridad, Instant fechaClasificacion) {
        Objects.requireNonNull(prioridad, "La prioridad del SLA es obligatoria");
        Objects.requireNonNull(fechaClasificacion, "La fecha de clasificacion es obligatoria");
        return new Sla(prioridad, fechaClasificacion.plus(Duration.ofHours(prioridad.horasSla())));
    }

    /** true si la fecha limite ya paso. El estado del ticket lo evalua {@link Ticket#slaVencido}. */
    public boolean vencidoA(Instant ahora) {
        return fechaLimite.isBefore(ahora);
    }
}
