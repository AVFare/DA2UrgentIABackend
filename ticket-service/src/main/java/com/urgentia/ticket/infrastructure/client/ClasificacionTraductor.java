package com.urgentia.ticket.infrastructure.client;

import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.ModuloAfectado;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.domain.model.Urgencia;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Anti-Corruption Layer: traduce el JSON de classification-service al Value Object
 * Clasificacion del dominio y rechaza todo lo que no cumpla el contrato
 * (enums fuera de la lista, confianza fuera de [0, 1], datos faltantes, otro ticketId).
 */
class ClasificacionTraductor {

    static final int JUSTIFICACION_MAX = 300;
    static final int PROVEEDOR_MAX = 100;

    private final Clock reloj;

    ClasificacionTraductor(Clock reloj) {
        this.reloj = reloj;
    }

    Clasificacion traducir(ClasificacionRespuesta r, TicketId ticketId) {
        if (r == null) {
            throw new RespuestaInvalidaException("la respuesta vino vacia");
        }
        if (r.ticketId() != null && !r.ticketId().equalsIgnoreCase(ticketId.toString())) {
            throw new RespuestaInvalidaException("la respuesta es de otro ticket: " + r.ticketId());
        }
        if (r.requiereEscalamiento() == null) {
            throw new RespuestaInvalidaException("falta requiereEscalamiento");
        }
        if (r.confianza() == null) {
            throw new RespuestaInvalidaException("falta confianza");
        }
        try {
            return new Clasificacion(
                    enumDe(Categoria.class, r.categoria(), "categoria"),
                    enumDe(Urgencia.class, r.urgencia(), "urgencia"),
                    enumDe(Impacto.class, r.impacto(), "impacto"),
                    enumDe(ModuloAfectado.class, r.moduloAfectado(), "moduloAfectado"),
                    r.requiereEscalamiento(),
                    r.confianza(),
                    recortar(r.justificacion(), JUSTIFICACION_MAX),
                    recortar(r.proveedor(), PROVEEDOR_MAX),
                    fecha(r.fecha()));
        } catch (ReglaDeNegocioException e) {
            throw new RespuestaInvalidaException(e.getMessage());
        }
    }

    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String valor, String campo) {
        if (valor == null) {
            throw new RespuestaInvalidaException("falta " + campo);
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            throw new RespuestaInvalidaException(campo + " tiene un valor fuera de la lista: " + valor);
        }
    }

    /** Si la IA no informa la fecha, se toma la hora actual (la clasificacion acaba de ocurrir). */
    private Instant fecha(String valor) {
        if (valor == null || valor.isBlank()) {
            return Instant.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        }
        try {
            return Instant.parse(valor).truncatedTo(ChronoUnit.SECONDS);
        } catch (DateTimeParseException e) {
            throw new RespuestaInvalidaException("fecha con formato invalido: " + valor);
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
