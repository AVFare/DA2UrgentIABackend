package com.urgentia.user.service;

/** El pedido trae datos que no se pueden usar (se responde 400 VALIDACION). */
public class DatosInvalidosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String campo;

    public DatosInvalidosException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
