package com.urgentia.ticket.infrastructure.soap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Configuracion del servicio SOAP de consulta de estado de tickets.
 *
 * <p>El servlet de Spring-WS, la ruta (/ws) y la publicacion del WSDL y el XSD los arma Spring Boot
 * a partir de application.yml (spring.webservices). No se usa @EnableWs a proposito: con esa anotacion
 * Boot desactiva su autoconfiguracion y habria que registrar el servlet y el WSDL a mano.
 *
 * <p>El contrato vive en src/main/resources/soap, una copia de contracts/soap: ContratoSoapTest
 * comprueba que sean iguales.
 */
@Configuration
public class SoapConfig {

    /** Convierte las excepciones del endpoint en SOAP Fault con el detalle del contrato. */
    @Bean
    public FaultExceptionResolver faultExceptionResolver() {
        FaultExceptionResolver resolver = new FaultExceptionResolver();
        resolver.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return resolver;
    }
}
