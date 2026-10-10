package com.urgentia.user.service;

/** Email inexistente, contraseña incorrecta o usuario inactivo: siempre el mismo error (401). */
public class CredencialesInvalidasException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CredencialesInvalidasException() {
        super("Email o contraseña invalidos");
    }
}
