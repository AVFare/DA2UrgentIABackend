package com.urgentia.ticket.infrastructure.persistence;

import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.ModuloAfectado;
import com.urgentia.ticket.domain.model.Prioridad;
import com.urgentia.ticket.domain.model.Urgencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla tickets. Es un detalle de infraestructura: el dominio nunca la ve.
 * La traduce TicketPersistenceMapper. La tabla la crea Flyway (db/migration).
 */
@Entity
@Table(name = "tickets")
public class TicketJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "descripcion", nullable = false, length = 2000)
    private String descripcion;

    @Column(name = "solicitante_id", nullable = false)
    private UUID solicitanteId;

    @Column(name = "agente_asignado_id")
    private UUID agenteAsignadoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoTicket estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad", length = 2)
    private Prioridad prioridad;

    @Column(name = "fecha_limite_sla")
    private Instant fechaLimiteSla;

    @Column(name = "requiere_revision_manual", nullable = false)
    private boolean requiereRevisionManual;

    @Column(name = "motivo_escalamiento", length = 500)
    private String motivoEscalamiento;

    // Clasificacion de la IA (columnas null mientras el ticket no este clasificado).

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", length = 20)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgencia", length = 10)
    private Urgencia urgencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "impacto", length = 10)
    private Impacto impacto;

    @Enumerated(EnumType.STRING)
    @Column(name = "modulo_afectado", length = 30)
    private ModuloAfectado moduloAfectado;

    @Column(name = "requiere_escalamiento")
    private Boolean requiereEscalamiento;

    @Column(name = "confianza")
    private Double confianza;

    @Column(name = "justificacion", length = 300)
    private String justificacion;

    @Column(name = "proveedor", length = 100)
    private String proveedor;

    @Column(name = "fecha_clasificacion")
    private Instant fechaClasificacion;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    /** Control de concurrencia optimista: dos cambios simultaneos sobre el mismo ticket no se pisan. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected TicketJpaEntity() {
        // Lo necesita JPA.
    }

    TicketJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public UUID getSolicitanteId() {
        return solicitanteId;
    }

    void setSolicitanteId(UUID solicitanteId) {
        this.solicitanteId = solicitanteId;
    }

    public UUID getAgenteAsignadoId() {
        return agenteAsignadoId;
    }

    void setAgenteAsignadoId(UUID agenteAsignadoId) {
        this.agenteAsignadoId = agenteAsignadoId;
    }

    public EstadoTicket getEstado() {
        return estado;
    }

    void setEstado(EstadoTicket estado) {
        this.estado = estado;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    void setPrioridad(Prioridad prioridad) {
        this.prioridad = prioridad;
    }

    public Instant getFechaLimiteSla() {
        return fechaLimiteSla;
    }

    void setFechaLimiteSla(Instant fechaLimiteSla) {
        this.fechaLimiteSla = fechaLimiteSla;
    }

    public boolean isRequiereRevisionManual() {
        return requiereRevisionManual;
    }

    void setRequiereRevisionManual(boolean requiereRevisionManual) {
        this.requiereRevisionManual = requiereRevisionManual;
    }

    public String getMotivoEscalamiento() {
        return motivoEscalamiento;
    }

    void setMotivoEscalamiento(String motivoEscalamiento) {
        this.motivoEscalamiento = motivoEscalamiento;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public Urgencia getUrgencia() {
        return urgencia;
    }

    void setUrgencia(Urgencia urgencia) {
        this.urgencia = urgencia;
    }

    public Impacto getImpacto() {
        return impacto;
    }

    void setImpacto(Impacto impacto) {
        this.impacto = impacto;
    }

    public ModuloAfectado getModuloAfectado() {
        return moduloAfectado;
    }

    void setModuloAfectado(ModuloAfectado moduloAfectado) {
        this.moduloAfectado = moduloAfectado;
    }

    public Boolean getRequiereEscalamiento() {
        return requiereEscalamiento;
    }

    void setRequiereEscalamiento(Boolean requiereEscalamiento) {
        this.requiereEscalamiento = requiereEscalamiento;
    }

    public Double getConfianza() {
        return confianza;
    }

    void setConfianza(Double confianza) {
        this.confianza = confianza;
    }

    public String getJustificacion() {
        return justificacion;
    }

    void setJustificacion(String justificacion) {
        this.justificacion = justificacion;
    }

    public String getProveedor() {
        return proveedor;
    }

    void setProveedor(String proveedor) {
        this.proveedor = proveedor;
    }

    public Instant getFechaClasificacion() {
        return fechaClasificacion;
    }

    void setFechaClasificacion(Instant fechaClasificacion) {
        this.fechaClasificacion = fechaClasificacion;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public Long getVersion() {
        return version;
    }

    void setVersion(Long version) {
        this.version = version;
    }
}
