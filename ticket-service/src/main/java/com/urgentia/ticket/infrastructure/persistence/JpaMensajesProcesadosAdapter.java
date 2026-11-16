package com.urgentia.ticket.infrastructure.persistence;

import com.urgentia.ticket.application.port.out.MensajesProcesadosPort;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter del puerto MensajesProcesadosPort sobre la tabla mensajes_procesados. La clave primaria
 * es el eventId: si dos entregas del mismo mensaje se procesaran a la vez, la base rechaza la
 * segunda, su transaccion se deshace y el mensaje se reintenta (y ahi ya figura como procesado).
 */
@Component
public class JpaMensajesProcesadosAdapter implements MensajesProcesadosPort {

    private final SpringDataMensajeProcesadoRepository repositorio;
    private final Clock reloj;

    public JpaMensajesProcesadosAdapter(SpringDataMensajeProcesadoRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public boolean yaProcesado(UUID mensajeId) {
        return repositorio.existsById(mensajeId);
    }

    @Override
    public void registrar(UUID mensajeId, String tipo) {
        // Siempre INSERT (ver MensajeProcesadoJpaEntity): si el eventId ya existe, falla en vez de pisarlo.
        repositorio.saveAndFlush(new MensajeProcesadoJpaEntity(mensajeId, tipo, Instant.now(reloj)));
    }
}
