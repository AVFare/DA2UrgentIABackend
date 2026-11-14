package com.urgentia.ticket.infrastructure.soap;

import com.urgentia.ticket.application.exception.DatosInvalidosException;
import com.urgentia.ticket.application.port.in.ConsultarTicketsUseCase;
import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.infrastructure.soap.generated.ConsultarEstadoTicketRequest;
import com.urgentia.ticket.infrastructure.soap.generated.ConsultarEstadoTicketResponse;
import com.urgentia.ticket.infrastructure.soap.generated.EstadoTicket;
import com.urgentia.ticket.infrastructure.soap.generated.Prioridad;
import java.time.temporal.ChronoUnit;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.DatatypeConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * Adaptador de entrada SOAP: consulta el estado de un ticket (contrato en contracts/soap).
 * Es un adaptador mas, igual que TicketController: traduce el pedido y delega en el caso de uso
 * de consulta, sin reglas propias. Las excepciones las convierte en SOAP Fault FaultExceptionResolver.
 */
@Endpoint
public class TicketsEndpoint {

    public static final String NAMESPACE = "http://urgentia.local/ws/tickets";

    private static final Logger log = LoggerFactory.getLogger(TicketsEndpoint.class);

    private final ConsultarTicketsUseCase consultar;
    private final UsuarioDelPedidoSoap usuarioDelPedido;
    private final DatatypeFactory fechas;

    public TicketsEndpoint(ConsultarTicketsUseCase consultar, UsuarioDelPedidoSoap usuarioDelPedido) {
        this.consultar = consultar;
        this.usuarioDelPedido = usuarioDelPedido;
        try {
            this.fechas = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("No se pudo crear la fabrica de fechas XML", e);
        }
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "ConsultarEstadoTicketRequest")
    @ResponsePayload
    public ConsultarEstadoTicketResponse consultarEstado(@RequestPayload ConsultarEstadoTicketRequest pedido) {
        TicketId id = parsearId(pedido.getTicketId());
        Ticket ticket = consultar.obtener(id, usuarioDelPedido.actual());
        log.info("SOAP ConsultarEstadoTicket ticketId={} -> estado={} prioridad={}",
                id, ticket.estado(), ticket.prioridad());
        return aRespuesta(ticket);
    }

    /** El id llega como texto: si falta o no es un UUID, es un error de validacion del pedido. */
    private static TicketId parsearId(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new DatosInvalidosException("ticketId", "El ticketId es obligatorio");
        }
        try {
            return TicketId.de(texto.strip());
        } catch (ReglaDeNegocioException e) {
            throw new DatosInvalidosException("ticketId", e.getMessage());
        }
    }

    private ConsultarEstadoTicketResponse aRespuesta(Ticket ticket) {
        ConsultarEstadoTicketResponse respuesta = new ConsultarEstadoTicketResponse();
        respuesta.setTicketId(ticket.id().toString());
        respuesta.setEstado(EstadoTicket.fromValue(ticket.estado().name()));
        // Prioridad y vencimiento del SLA solo existen una vez que el ticket esta clasificado.
        if (ticket.prioridad() != null) {
            respuesta.setPrioridad(Prioridad.fromValue(ticket.prioridad().name()));
        }
        if (ticket.fechaLimiteSla() != null) {
            // Instant.toString() ya sale en UTC con Z: 2026-10-05T15:03:11Z
            respuesta.setFechaLimiteSla(
                    fechas.newXMLGregorianCalendar(ticket.fechaLimiteSla().truncatedTo(ChronoUnit.SECONDS).toString()));
        }
        return respuesta;
    }
}
