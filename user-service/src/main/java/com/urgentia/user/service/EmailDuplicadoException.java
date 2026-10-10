package com.urgentia.user.service;

/** Ya existe un usuario con ese email (409). */
public class EmailDuplicadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailDuplicadoException(String email) {
        super("Ya existe un usuario con el email " + email);
    }
}
