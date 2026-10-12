package com.urgentia.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fila de la tabla usuarios. La tabla la crea Flyway (db/migration). */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "email", nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_alta", nullable = false)
    private Instant fechaAlta;

    protected Usuario() {
        // Lo necesita JPA.
    }

    public Usuario(UUID id, String nombre, String email, String passwordHash, Rol rol, boolean activo,
                   Instant fechaAlta) {
        this.id = id;
        this.nombre = nombre;
        this.email = email;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.activo = activo;
        this.fechaAlta = fechaAlta;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getFechaAlta() {
        return fechaAlta;
    }
}
