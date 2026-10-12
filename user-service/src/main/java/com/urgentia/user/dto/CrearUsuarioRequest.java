package com.urgentia.user.dto;

import com.urgentia.user.model.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(
        @NotBlank @Size(max = 120) @Schema(example = "Ana Agente") String nombre,
        @NotBlank @Email @Size(max = 254) @Schema(example = "ana@urgentia.local") String email,
        @NotBlank @Size(min = 8, message = "debe tener al menos 8 caracteres")
        @Schema(example = "Segura123!", description = "Minimo propuesto por P3, pendiente de acordar.")
        String password,
        @NotNull Rol rol) {
}
