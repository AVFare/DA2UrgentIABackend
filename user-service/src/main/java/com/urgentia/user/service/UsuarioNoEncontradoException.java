package com.urgentia.user.service;

import java.util.UUID;

/** El usuario no existe (404). */
public class UsuarioNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UsuarioNoEncontradoException(UUID id) {
        super("No existe el usuario " + id);
    }
}
