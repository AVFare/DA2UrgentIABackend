package com.urgentia.ticket.infrastructure.soap;

import com.urgentia.ticket.application.exception.DatosInvalidosException;
import com.urgentia.ticket.application.exception.TicketNoEncontradoException;
import com.urgentia.ticket.infrastructure.rest.CorrelationIdFilter;
import com.urgentia.ticket.infrastructure.soap.generated.CodigoError;
import com.urgentia.ticket.infrastructure.soap.generated.ErrorDetalle;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.server.endpoint.AbstractSoapFaultDefinitionExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;

/**
 * Convierte las excepciones del endpoint en SOAP Fault, con el detalle ErrorDetalle del contrato
 * (codigo, mensaje y correlationId). Es el equivalente SOAP de GlobalExceptionHandler.
 *
 * <p>Ticket inexistente (o ajeno para un SOLICITANTE) y datos invalidos son errores del cliente
 * (soap:Client); cualquier otra falla es del servidor (soap:Server) y no muestra detalles internos.
 */
public class FaultExceptionResolver extends AbstractSoapFaultDefinitionExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(FaultExceptionResolver.class);

    private final JAXBContext jaxb;

    public FaultExceptionResolver() {
        try {
            this.jaxb = JAXBContext.newInstance(ErrorDetalle.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("No se pudo crear el contexto JAXB de ErrorDetalle", e);
        }
    }

    @Override
    protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception ex) {
        SoapFaultDefinition definicion = new SoapFaultDefinition();
        if (ex instanceof TicketNoEncontradoException || ex instanceof DatosInvalidosException) {
            definicion.setFaultCode(SoapFaultDefinition.CLIENT);
            definicion.setFaultStringOrReason(ex.getMessage());
            log.warn("SOAP Fault {}: {}", codigo(ex), ex.getMessage());
        } else {
            definicion.setFaultCode(SoapFaultDefinition.SERVER);
            definicion.setFaultStringOrReason("Error interno");
            log.error("SOAP Fault ERROR_INTERNO", ex);
        }
        return definicion;
    }

    @Override
    protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
        boolean conocido = ex instanceof TicketNoEncontradoException || ex instanceof DatosInvalidosException;
        ErrorDetalle detalle = new ErrorDetalle();
        detalle.setCodigo(codigo(ex));
        detalle.setMensaje(conocido ? ex.getMessage() : "Error interno");
        detalle.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_CLAVE));
        SoapFaultDetail contenedor = fault.addFaultDetail();
        try {
            jaxb.createMarshaller().marshal(detalle, contenedor.getResult());
        } catch (JAXBException e) {
            throw new IllegalStateException("No se pudo escribir el detalle del SOAP Fault", e);
        }
    }

    private static CodigoError codigo(Exception ex) {
        if (ex instanceof TicketNoEncontradoException) {
            return CodigoError.NO_ENCONTRADO;
        }
        if (ex instanceof DatosInvalidosException) {
            return CodigoError.VALIDACION;
        }
        return CodigoError.ERROR_INTERNO;
    }
}
