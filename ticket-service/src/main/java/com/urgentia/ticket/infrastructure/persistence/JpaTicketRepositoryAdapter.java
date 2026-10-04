package com.urgentia.ticket.infrastructure.persistence;

import com.urgentia.ticket.application.port.out.FiltroTickets;
import com.urgentia.ticket.application.port.out.Pagina;
import com.urgentia.ticket.application.port.out.TicketRepository;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Adapter del patron Repository: implementa el puerto TicketRepository con Spring Data JPA
 * sobre PostgreSQL. La capa de aplicacion no sabe que existe.
 */
@Component
public class JpaTicketRepositoryAdapter implements TicketRepository {

    /**
     * Prioridad ascendente (P1 primero) y, a igual prioridad, lo mas nuevo primero.
     * En PostgreSQL los tickets sin prioridad (pendientes) quedan al final.
     * El id desempata para que la paginacion sea estable.
     */
    static final Sort ORDEN = Sort.by(
            Sort.Order.asc("prioridad"),
            Sort.Order.desc("fechaCreacion"),
            Sort.Order.asc("id"));

    private final SpringDataTicketRepository jpa;

    public JpaTicketRepositoryAdapter(SpringDataTicketRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Ticket guardar(Ticket ticket) {
        // saveAndFlush: si otro pedido cambio el ticket en el medio, la version no coincide
        // y salta ObjectOptimisticLockingFailureException aca mismo.
        TicketJpaEntity guardada = jpa.saveAndFlush(TicketPersistenceMapper.aEntidad(ticket));
        return TicketPersistenceMapper.aDominio(guardada);
    }

    @Override
    public Optional<Ticket> buscarPorId(TicketId id) {
        return jpa.findById(id.valor()).map(TicketPersistenceMapper::aDominio);
    }

    @Override
    public Pagina<Ticket> buscar(FiltroTickets filtro, int pagina, int tamanio) {
        Page<TicketJpaEntity> resultado = jpa.findAll(conFiltros(filtro), PageRequest.of(pagina, tamanio, ORDEN));
        List<Ticket> tickets = resultado.getContent().stream().map(TicketPersistenceMapper::aDominio).toList();
        return new Pagina<>(tickets, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    private static Specification<TicketJpaEntity> conFiltros(FiltroTickets filtro) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.estado() != null) {
                condiciones.add(cb.equal(root.get("estado"), filtro.estado()));
            }
            if (filtro.prioridad() != null) {
                condiciones.add(cb.equal(root.get("prioridad"), filtro.prioridad()));
            }
            if (filtro.categoria() != null) {
                condiciones.add(cb.equal(root.get("categoria"), filtro.categoria()));
            }
            if (filtro.solicitanteId() != null) {
                condiciones.add(cb.equal(root.get("solicitanteId"), filtro.solicitanteId()));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }
}
