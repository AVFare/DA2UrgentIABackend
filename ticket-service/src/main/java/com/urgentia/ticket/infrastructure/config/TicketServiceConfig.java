package com.urgentia.ticket.infrastructure.config;

import com.urgentia.ticket.application.port.out.ClasificadorPort;
import com.urgentia.ticket.application.port.out.EventPublisherPort;
import com.urgentia.ticket.application.port.out.MensajesProcesadosPort;
import com.urgentia.ticket.application.port.out.SolicitudDeClasificacionPort;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.application.service.AplicarClasificacionService;
import com.urgentia.ticket.application.service.AsignarTicketService;
import com.urgentia.ticket.application.service.CambiarEstadoService;
import com.urgentia.ticket.application.service.ConsultarTicketsService;
import com.urgentia.ticket.application.service.CrearTicketService;
import com.urgentia.ticket.application.service.ModoClasificacion;
import com.urgentia.ticket.application.service.ReclasificarTicketService;
import com.urgentia.ticket.domain.factory.TicketFactory;
import com.urgentia.ticket.domain.service.MatrizItilStrategy;
import com.urgentia.ticket.domain.service.PrioridadStrategy;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Arma el hexagono: crea las piezas del dominio y los casos de uso (que no conocen
 * Spring) y les conecta los adaptadores de infraestructura.
 */
@Configuration
public class TicketServiceConfig {

    /** Reloj en UTC que avanza de a segundos: las fechas salen como 2026-10-05T14:03:11Z. */
    @Bean
    public Clock reloj() {
        return Clock.tickSeconds(ZoneOffset.UTC);
    }

    @Bean
    public PrioridadStrategy prioridadStrategy() {
        return new MatrizItilStrategy();
    }

    @Bean
    public TicketFactory ticketFactory(Clock reloj) {
        return new TicketFactory(reloj);
    }

    @Bean
    public CasosDeUsoTransaccionales casosDeUso(TicketFactory factory, ClasificadorPort clasificador,
                                                PrioridadStrategy estrategia, TicketRepository repositorio,
                                                EventPublisherPort publicador,
                                                SolicitudDeClasificacionPort solicitudes,
                                                MensajesProcesadosPort mensajesProcesados,
                                                @Value("${clasificacion.modo:async}") String modo, Clock reloj,
                                                PlatformTransactionManager transacciones) {
        TransactionTemplate escritura = new TransactionTemplate(transacciones);
        TransactionTemplate lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        return new CasosDeUsoTransaccionales(
                new CrearTicketService(factory, clasificador, estrategia, repositorio, publicador, solicitudes,
                        ModoClasificacion.desde(modo), reloj),
                new AsignarTicketService(repositorio, publicador, reloj),
                new CambiarEstadoService(repositorio, publicador, reloj),
                new ReclasificarTicketService(clasificador, estrategia, repositorio, publicador, reloj),
                new ConsultarTicketsService(repositorio),
                new AplicarClasificacionService(repositorio, mensajesProcesados, estrategia, publicador, reloj),
                escritura,
                lectura);
    }
}
