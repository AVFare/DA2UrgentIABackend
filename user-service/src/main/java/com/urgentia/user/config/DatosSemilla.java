package com.urgentia.user.config;

import com.urgentia.user.model.Rol;
import com.urgentia.user.model.Usuario;
import com.urgentia.user.repository.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Carga los 5 usuarios semilla (seccion 6.2 del contexto) si todavia no existen, con IDs fijos
 * para que coincidan con los ejemplos de los contratos (p.ej. solicitanteId en ticket-service.yaml).
 * Idempotente: no duplica nada si ya estan cargados (ni por id ni por email).
 */
@Component
public class DatosSemilla implements ApplicationRunner {

    private record Semilla(UUID id, String nombre, String email, String password, Rol rol) {
    }

    private static final List<Semilla> USUARIOS = List.of(
            new Semilla(UUID.fromString("b1c2d3e4-0000-4000-8000-000000000001"), "Ada Admin",
                    "admin@urgentia.local", "Admin123!", Rol.ADMIN),
            new Semilla(UUID.fromString("b1c2d3e4-0000-4000-8000-000000000002"), "Guido Guardia",
                    "guardia@urgentia.local", "Agente123!", Rol.AGENTE),
            new Semilla(UUID.fromString("b1c2d3e4-0000-4000-8000-000000000003"), "Sara Soporte",
                    "soporte@urgentia.local", "Agente123!", Rol.AGENTE),
            new Semilla(UUID.fromString("b1c2d3e4-0000-4000-8000-000000000004"), "Sofía Solicitante",
                    "solicitante@urgentia.local", "Usuario123!", Rol.SOLICITANTE),
            new Semilla(UUID.fromString("b1c2d3e4-0000-4000-8000-000000000005"), "Santiago Solicitante",
                    "solicitante2@urgentia.local", "Usuario123!", Rol.SOLICITANTE));

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public DatosSemilla(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Semilla semilla : USUARIOS) {
            if (usuarios.existsById(semilla.id()) || usuarios.existsByEmailIgnoreCase(semilla.email())) {
                continue;
            }
            usuarios.save(new Usuario(semilla.id(), semilla.nombre(), semilla.email(),
                    passwordEncoder.encode(semilla.password()), semilla.rol(), true, Instant.now()));
        }
    }
}
