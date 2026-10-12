package com.urgentia.user.repository;

import com.urgentia.user.model.Rol;
import com.urgentia.user.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Page<Usuario> findByRol(Rol rol, Pageable pageable);
}
