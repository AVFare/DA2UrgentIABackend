package com.urgentia.ticket.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urgentia.ticket.application.port.in.AplicarClasificacionUseCase;
import com.urgentia.ticket.application.port.in.AplicarClasificacionUseCase.ClasificacionRecibida;
import com.urgentia.ticket.application.port.in.AplicarClasificacionUseCase.Resultado;
import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import com.urgentia.ticket.domain.exception.TransicionInvalidaException;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.infrastructure.client.ClasificacionRespuesta;
import com.urgentia.ticket.infrastructure.client.ClasificacionTraductor;
import com.urgentia.ticket.infrastructure.client.RespuestaInvalidaException;
import com.urgentia.ticket.infrastructure.rest.CorrelationIdFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Decide que hacer con cada ClasificacionGenerada de la cola tickets.clasificaciones. No conoce
 * RabbitMQ: devuelve una {@link Accion} y ConsumidorDeClasificaciones la traduce a ack o reject
 * (reglas para quien consume de contracts/messaging/README.md).
 *
 * <ul>
 *   <li>Valida el sobre contra el contrato y traduce la clasificacion con la misma ACL que la
 *       respuesta REST (ClasificacionTraductor). Si no cumple: DESCARTAR (va a la DLQ).</li>
 *   <li>Aplica el mensaje con AplicarClasificacionUseCase, en una transaccion. Si sale bien, ya
 *       estaba procesado o el ticket no existe: CONFIRMAR.</li>
 *   <li>Si falla algo transitorio (la base, un choque de versiones): REINTENTAR.</li>
 * </ul>
 */
@Component
public class ManejadorDeClasificaciones {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeClasificaciones.class);
    private static final String SOURCE_ESPERADO = "classification-service";

    /** Que hacer con el mensaje en el broker. */
    public enum Accion {
        /** ack: se proceso (o ya estaba procesado). */
        CONFIRMAR,
        /** reject sin reencolar: no cumple el contrato y reintentarlo no lo arregla; va a la DLQ. */
        DESCARTAR,
        /** reject con reencolado: fallo algo transitorio; RabbitMQ lo reintenta hasta 5 veces. */
        REINTENTAR
    }

    private final AplicarClasificacionUseCase aplicarClasificacion;
    private final ObjectMapper json;
    private final ClasificacionTraductor traductor;

    public ManejadorDeClasificaciones(AplicarClasificacionUseCase aplicarClasificacion, ObjectMapper json,
                                      Clock reloj) {
        this.aplicarClasificacion = aplicarClasificacion;
        this.json = json;
        this.traductor = new ClasificacionTraductor(reloj);
    }

    public Accion manejar(byte[] cuerpo) {
        JsonNode sobre;
        try {
            sobre = json.readTree(cuerpo);
        } catch (IOException e) {
            log.warn("ClasificacionGenerada descartada: el cuerpo no es JSON");
            return Accion.DESCARTAR;
        }
        if (sobre == null || !sobre.isObject()) {
            log.warn("ClasificacionGenerada descartada: el cuerpo no es un objeto JSON");
            return Accion.DESCARTAR;
        }
        String correlationId = sobre.path("correlationId").asText("");
        if (!correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_CLAVE, correlationId);
        }
        try {
            ClasificacionRecibida recibida;
            try {
                recibida = traducir(sobre);
            } catch (MensajeInvalidoException | RespuestaInvalidaException e) {
                log.warn("ClasificacionGenerada {} descartada: {}", sobre.path("eventId").asText("?"), e.getMessage());
                return Accion.DESCARTAR;
            }
            return aplicar(recibida);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_CLAVE);
        }
    }

    private Accion aplicar(ClasificacionRecibida recibida) {
        try {
            Resultado resultado = aplicarClasificacion.aplicar(recibida);
            switch (resultado) {
                case APLICADA -> log.info("Clasificacion aplicada al ticket {} (mensaje {})", recibida.ticketId(),
                        recibida.mensajeId());
                case QUEDA_PENDIENTE -> log.warn("La IA no pudo clasificar el ticket {}: {}. Queda en "
                        + "PENDIENTE_CLASIFICACION para reclasificar", recibida.ticketId(), recibida.error());
                case DUPLICADA -> log.info("Mensaje {} repetido: ya se habia procesado, se confirma sin efectos",
                        recibida.mensajeId());
                case IGNORADA -> log.info("El ticket {} ya no esta pendiente de clasificacion: se ignora el mensaje {}",
                        recibida.ticketId(), recibida.mensajeId());
                case TICKET_INEXISTENTE -> log.warn("No existe el ticket {}: se confirma el mensaje {} sin efectos",
                        recibida.ticketId(), recibida.mensajeId());
                default -> throw new IllegalStateException("Resultado desconocido: " + resultado);
            }
            return Accion.CONFIRMAR;
        } catch (ReglaDeNegocioException | TransicionInvalidaException e) {
            log.warn("ClasificacionGenerada {} descartada: {}", recibida.mensajeId(), e.getMessage());
            return Accion.DESCARTAR;
        } catch (RuntimeException e) {
            log.error("No se pudo aplicar la ClasificacionGenerada {}: {}. Se reintenta", recibida.mensajeId(),
                    e.getMessage(), e);
            return Accion.REINTENTAR;
        }
    }

    /** Anti-Corruption Layer del mensaje: valida el sobre y traduce el payload al dominio. */
    private ClasificacionRecibida traducir(JsonNode sobre) {
        exigir(TopologiaRabbit.CLASIFICACION_GENERADA.equals(sobre.path("eventType").asText()),
                "eventType tiene que ser ClasificacionGenerada");
        exigir(sobre.path("version").isInt() && sobre.path("version").asInt() == EventoEnvelope.VERSION,
                "version tiene que ser 1");
        exigir(SOURCE_ESPERADO.equals(sobre.path("source").asText()), "source tiene que ser classification-service");
        exigir(!sobre.path("correlationId").asText("").isBlank(), "falta correlationId");
        UUID mensajeId = uuid(sobre.path("eventId"), "eventId");

        JsonNode payload = sobre.path("payload");
        exigir(payload.isObject(), "falta payload");
        TicketId ticketId = TicketId.de(uuid(payload.path("ticketId"), "payload.ticketId"));
        String resultado = payload.path("resultado").asText();

        if ("CLASIFICADO".equals(resultado)) {
            JsonNode c = payload.path("clasificacion");
            exigir(c.isObject(), "con resultado CLASIFICADO tiene que venir clasificacion");
            ClasificacionRespuesta respuesta = new ClasificacionRespuesta(ticketId.toString(),
                    textoONull(c, "categoria"), textoONull(c, "urgencia"), textoONull(c, "impacto"),
                    textoONull(c, "moduloAfectado"),
                    c.path("requiereEscalamiento").isBoolean() ? c.path("requiereEscalamiento").booleanValue() : null,
                    c.path("confianza").isNumber() ? c.path("confianza").doubleValue() : null,
                    textoONull(c, "justificacion"), textoONull(c, "proveedor"), textoONull(c, "fecha"));
            return ClasificacionRecibida.clasificada(mensajeId, ticketId, traductor.traducir(respuesta, ticketId));
        }
        if ("ERROR".equals(resultado)) {
            JsonNode error = payload.path("error");
            exigir(error.isObject(), "con resultado ERROR tiene que venir error");
            String codigo = error.path("codigo").asText("ERROR");
            String detalle = error.path("mensaje").asText("");
            return ClasificacionRecibida.conError(mensajeId, ticketId, detalle.isBlank() ? codigo : codigo + ": " + detalle);
        }
        throw new MensajeInvalidoException("resultado tiene que ser CLASIFICADO o ERROR, no '" + resultado + "'");
    }

    private static UUID uuid(JsonNode nodo, String campo) {
        exigir(nodo.isTextual(), "falta " + campo);
        try {
            return UUID.fromString(nodo.asText());
        } catch (IllegalArgumentException e) {
            throw new MensajeInvalidoException(campo + " no es un UUID: " + nodo.asText());
        }
    }

    private static String textoONull(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        return valor.isTextual() ? valor.asText() : null;
    }

    private static void exigir(boolean condicion, String motivo) {
        if (!condicion) {
            throw new MensajeInvalidoException(motivo);
        }
    }

    /** El mensaje no cumple contracts/events/clasificacion-events.schema.json. */
    static class MensajeInvalidoException extends RuntimeException {

        MensajeInvalidoException(String mensaje) {
            super(mensaje);
        }
    }
}
