package com.urgentia.ticket.application.service;

import java.util.Locale;

/**
 * Como clasifica el alta de un ticket (variable CLASIFICACION_MODO).
 *
 * <ul>
 *   <li>{@code async} (por defecto): el POST responde enseguida en PENDIENTE_CLASIFICACION y pide la
 *       clasificacion por la cola; la prioridad aparece cuando llega ClasificacionGenerada.</li>
 *   <li>{@code sync}: el plan B. Llama a la IA por REST dentro del POST, como en la Entrega 1.</li>
 * </ul>
 */
public enum ModoClasificacion {
    SINCRONICO,
    ASINCRONICO;

    public static ModoClasificacion desde(String valor) {
        String normalizado = valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT);
        return switch (normalizado) {
            case "sync" -> SINCRONICO;
            case "async" -> ASINCRONICO;
            default -> throw new IllegalArgumentException(
                    "CLASIFICACION_MODO tiene que ser sync o async, no '" + valor + "'");
        };
    }
}
