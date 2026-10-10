package com.urgentia.ticket.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Agente al que se asigna el ticket")
public record AsignarTicketRequest(
        @Schema(example = "b1c2d3e4-0000-4000-8000-000000000002")
        @NotNull(message = "es obligatorio")
        UUID agenteId) {
}
