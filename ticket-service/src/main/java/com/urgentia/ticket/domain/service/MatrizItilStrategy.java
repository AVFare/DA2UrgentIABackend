package com.urgentia.ticket.domain.service;

import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.Prioridad;
import com.urgentia.ticket.domain.model.Urgencia;

/**
 * Matriz de prioridad estilo ITIL (seccion 8.1 del contexto).
 *
 * <pre>
 * Urgencia \ Impacto | ALTO | MEDIO | BAJO
 * ALTA               |  P1  |  P2   |  P3
 * MEDIA              |  P2  |  P3   |  P4
 * BAJA               |  P3  |  P4   |  P4
 * </pre>
 */
public class MatrizItilStrategy implements PrioridadStrategy {

    @Override
    public Prioridad calcular(Urgencia urgencia, Impacto impacto) {
        if (urgencia == null || impacto == null) {
            throw new ReglaDeNegocioException("Para calcular la prioridad hacen falta urgencia e impacto");
        }
        return switch (urgencia) {
            case ALTA -> switch (impacto) {
                case ALTO -> Prioridad.P1;
                case MEDIO -> Prioridad.P2;
                case BAJO -> Prioridad.P3;
            };
            case MEDIA -> switch (impacto) {
                case ALTO -> Prioridad.P2;
                case MEDIO -> Prioridad.P3;
                case BAJO -> Prioridad.P4;
            };
            case BAJA -> switch (impacto) {
                case ALTO -> Prioridad.P3;
                case MEDIO, BAJO -> Prioridad.P4;
            };
        };
    }
}
