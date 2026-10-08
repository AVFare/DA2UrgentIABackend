package com.urgentia.user.dto;

import com.urgentia.user.model.Rol;
import com.urgentia.user.model.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Nunca incluye la contraseña ni el hash. */
@Schema(name = "Usuario")
public record UsuarioResponse(
        UUID id,
        String nombre,
        String email,
        Rol rol,
        boolean activo,
        Instant fechaAlta) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(),
                usuario.isActivo(), usuario.getFechaAlta());
    }
}
