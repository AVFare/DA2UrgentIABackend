package com.urgentia.ticket.domain.factory;

import static org.assertj.core.api.Assertions.assertThat;

import com.urgentia.ticket.domain.event.TicketCreado;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Ticket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketFactoryTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T14:03:10Z");
    private final TicketFactory factory = new TicketFactory(Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void creaUnTicketNuevoConIdFechaYEventoTicketCreado() {
        UUID solicitante = UUID.randomUUID();

        Ticket ticket = factory.crear("No puede ingresar nadie", "Nadie puede entrar al sistema desde las 9", solicitante);

        assertThat(ticket.id()).isNotNull();
        assertThat(ticket.estado()).isEqualTo(EstadoTicket.NUEVO);
        assertThat(ticket.solicitanteId()).isEqualTo(solicitante);
        assertThat(ticket.fechaCreacion()).isEqualTo(AHORA);
        assertThat(ticket.fechaActualizacion()).isEqualTo(AHORA);
        assertThat(ticket.version()).isNull();
        assertThat(ticket.eventosPendientes()).hasSize(1).first().isInstanceOf(TicketCreado.class);
    }

    @Test
    void cadaTicketTieneSuPropioId() {
        Ticket uno = factory.crear("Primer ticket", "Descripcion de prueba 1", UUID.randomUUID());
        Ticket otro = factory.crear("Segundo ticket", "Descripcion de prueba 2", UUID.randomUUID());

        assertThat(uno.id()).isNotEqualTo(otro.id());
    }
}
