package com.urgentia.ticket.domain.model;

import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import java.time.Instant;

/**
 * Resultado de la IA (Value Object inmutable): lo que la IA <em>sugiere</em>.
 * No incluye la prioridad: la prioridad la decide el dominio.
 */
public record Clasificacion(
        Categoria categoria,
        Urgencia urgencia,
        Impacto impacto,
        ModuloAfectado moduloAfectado,
        boolean requiereEscalamiento,
        double confianza,
        String justificacion,
        String proveedor,
        Instant fecha) {

    /** Por debajo de este valor de confianza el ticket se marca para revision manual (seccion 8.3). */
    public static final double CONFIANZA_MINIMA = 0.6;

    public Clasificacion {
        requerido(categoria, "categoria");
        requerido(urgencia, "urgencia");
        requerido(impacto, "impacto");
        requerido(moduloAfectado, "moduloAfectado");
        requerido(fecha, "fecha");
        if (Double.isNaN(confianza) || confianza < 0.0 || confianza > 1.0) {
            throw new ReglaDeNegocioException("La confianza debe estar entre 0 y 1 y fue " + confianza);
        }
    }

    /** true si la IA no esta lo bastante segura y un agente deberia revisar la clasificacion. */
    public boolean requiereRevisionManual() {
        return confianza < CONFIANZA_MINIMA;
    }

    private static void requerido(Object valor, String campo) {
        if (valor == null) {
            throw new ReglaDeNegocioException("La clasificacion no tiene " + campo);
        }
    }
}
