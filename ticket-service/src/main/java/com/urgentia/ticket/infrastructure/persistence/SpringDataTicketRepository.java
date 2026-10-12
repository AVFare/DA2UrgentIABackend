package com.urgentia.ticket.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repositorio que genera Spring Data. Solo lo usa el adaptador JpaTicketRepositoryAdapter. */
interface SpringDataTicketRepository
        extends JpaRepository<TicketJpaEntity, UUID>, JpaSpecificationExecutor<TicketJpaEntity> {
}
