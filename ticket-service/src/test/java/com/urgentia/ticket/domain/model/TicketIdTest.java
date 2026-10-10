package com.urgentia.ticket.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.domain.exception.ReglaDeNegocioException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketIdTest {

    @Test
    void seCreaDesdeUnTextoConFormatoUuid() {
        TicketId id = TicketId.de("7c9e6679-7425-40de-944b-e07fc1f90ae7");

        assertThat(id.valor()).isEqualTo(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"));
        assertThat(id).hasToString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
        assertThat(id).isEqualTo(TicketId.de(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7")));
    }

    @Test
    void rechazaTextosQueNoSonUuid() {
        assertThatThrownBy(() -> TicketId.de("no-es-un-uuid")).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> TicketId.de((String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TicketId.de((UUID) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void cadaIdNuevoEsDistinto() {
        assertThat(TicketId.nuevo()).isNotEqualTo(TicketId.nuevo());
    }
}
