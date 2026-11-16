package com.urgentia.ticket.infrastructure.messaging.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Repositorio que genera Spring Data para la tabla outbox. */
interface SpringDataOutboxRepository extends JpaRepository<OutboxJpaEntity, Long> {

    /** Las pendientes, de la mas vieja a la mas nueva: se publican en el orden en que se guardaron. */
    @Transactional(readOnly = true)
    List<OutboxJpaEntity> findByPublicadoEnIsNullOrderByIdAsc(Limit limite);

    @Transactional
    @Modifying
    @Query("update OutboxJpaEntity o set o.publicadoEn = :cuando, o.intentos = o.intentos + 1, o.ultimoError = null "
            + "where o.id = :id")
    int marcarPublicado(@Param("id") Long id, @Param("cuando") Instant cuando);

    @Transactional
    @Modifying
    @Query("update OutboxJpaEntity o set o.intentos = o.intentos + 1, o.ultimoError = :error where o.id = :id")
    int registrarFallo(@Param("id") Long id, @Param("error") String error);

    @Transactional(readOnly = true)
    long countByPublicadoEnIsNull();
}
