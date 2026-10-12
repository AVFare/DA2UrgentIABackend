package com.urgentia.ticket.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear un ticket. El solicitante sale del token (header X-User-Id).")
public record CrearTicketRequest(
        @Schema(example = "No puede ingresar nadie", minLength = 5, maxLength = 120)
        @NotBlank(message = "es obligatorio")
        @Size(min = 5, max = 120, message = "debe tener entre 5 y 120 caracteres")
        String titulo,

        @Schema(example = "Producción caída, todos los usuarios bloqueados en el login desde las 9",
                minLength = 10, maxLength = 2000)
        @NotBlank(message = "es obligatoria")
        @Size(min = 10, max = 2000, message = "debe tener entre 10 y 2000 caracteres")
        String descripcion) {
}
