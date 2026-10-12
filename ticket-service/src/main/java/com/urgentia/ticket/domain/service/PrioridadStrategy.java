package com.urgentia.ticket.domain.service;

import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.Prioridad;
import com.urgentia.ticket.domain.model.Urgencia;

/**
 * Strategy: regla para calcular la prioridad a partir de urgencia e impacto.
 * Hoy hay una sola implementacion (la matriz ITIL); cambiar la regla no toca el agregado.
 */
public interface PrioridadStrategy {

    Prioridad calcular(Urgencia urgencia, Impacto impacto);
}
