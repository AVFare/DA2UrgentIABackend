package com.urgentia.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email @Schema(example = "solicitante@urgentia.local") String email,
        @NotBlank @Schema(example = "Usuario123!") String password) {
}
