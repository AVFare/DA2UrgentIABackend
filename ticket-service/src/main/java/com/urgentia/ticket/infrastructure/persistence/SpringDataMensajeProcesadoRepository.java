package com.urgentia.ticket.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio que genera Spring Data. Solo lo usa JpaMensajesProcesadosAdapter. */
interface SpringDataMensajeProcesadoRepository extends JpaRepository<MensajeProcesadoJpaEntity, UUID> {
}
