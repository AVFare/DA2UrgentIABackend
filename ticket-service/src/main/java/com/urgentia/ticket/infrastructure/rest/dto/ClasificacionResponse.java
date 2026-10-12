package com.urgentia.ticket.infrastructure.rest.dto;

import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.ModuloAfectado;
import com.urgentia.ticket.domain.model.Urgencia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Lo que sugirio la IA. No incluye la prioridad: la calcula el dominio.")
public record ClasificacionResponse(
        @Schema(example = "INCIDENTE") Categoria categoria,
        @Schema(example = "ALTA") Urgencia urgencia,
        @Schema(example = "ALTO") Impacto impacto,
        @Schema(example = "AUTENTICACION") ModuloAfectado moduloAfectado,
        @Schema(example = "true") boolean requiereEscalamiento,
        @Schema(example = "0.93") double confianza,
        @Schema(example = "Caída total del login en producción que afecta a todos los usuarios") String justificacion,
        @Schema(example = "mock") String proveedor,
        @Schema(example = "2026-10-05T14:03:11Z") Instant fecha) {
}
