package com.urgentia.ticket.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados del ticket y tabla de transiciones permitidas (seccion 8.4 del contexto).
 * Cualquier transicion que no figure aca es invalida.
 */
public enum EstadoTicket {
    NUEVO,
    PENDIENTE_CLASIFICACION,
    CLASIFICADO,
    ASIGNADO,
    EN_CURSO,
    ESCALADO,
    RESUELTO,
    CERRADO;

    private static final Map<EstadoTicket, Set<EstadoTicket>> TRANSICIONES = new EnumMap<>(EstadoTicket.class);

    static {
        TRANSICIONES.put(NUEVO, EnumSet.of(CLASIFICADO, PENDIENTE_CLASIFICACION));
        TRANSICIONES.put(PENDIENTE_CLASIFICACION, EnumSet.of(CLASIFICADO));
        TRANSICIONES.put(CLASIFICADO, EnumSet.of(ASIGNADO, ESCALADO));
        TRANSICIONES.put(ASIGNADO, EnumSet.of(EN_CURSO, ESCALADO));
        TRANSICIONES.put(EN_CURSO, EnumSet.of(RESUELTO, ESCALADO));
        TRANSICIONES.put(ESCALADO, EnumSet.of(EN_CURSO));
        TRANSICIONES.put(RESUELTO, EnumSet.of(CERRADO, EN_CURSO));
        TRANSICIONES.put(CERRADO, EnumSet.noneOf(EstadoTicket.class));
    }

    /** true si la tabla de la seccion 8.4 permite pasar de este estado a {@code destino}. */
    public boolean puedePasarA(EstadoTicket destino) {
        return destino != null && TRANSICIONES.get(this).contains(destino);
    }

    /** Estados a los que se puede pasar desde este (sin importar como). */
    public Set<EstadoTicket> siguientesPosibles() {
        return Collections.unmodifiableSet(TRANSICIONES.get(this));
    }

    /** Abierto = todo estado distinto de RESUELTO y CERRADO. */
    public boolean estaAbierto() {
        return this != RESUELTO && this != CERRADO;
    }
}
