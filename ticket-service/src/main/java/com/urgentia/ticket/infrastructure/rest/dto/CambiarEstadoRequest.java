package com.urgentia.ticket.infrastructure.rest.dto;

import com.urgentia.ticket.domain.model.EstadoTicket;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Nuevo estado del ticket. El motivo es obligatorio solo para ESCALADO.")
public record CambiarEstadoRequest(
        @Schema(example = "ESCALADO")
        @NotNull(message = "es obligatorio")
        EstadoTicket estado,

        @Schema(example = "El cliente reporta pérdida de datos", maxLength = 500)
        @Size(max = 500, message = "puede tener hasta 500 caracteres")
        String motivo) {
}
