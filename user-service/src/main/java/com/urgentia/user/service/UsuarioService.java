package com.urgentia.user.service;

import com.urgentia.user.model.Rol;
import com.urgentia.user.model.Usuario;
import com.urgentia.user.repository.UsuarioRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario crear(String nombre, String email, String password, Rol rol) {
        String emailNormalizado = email.toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new EmailDuplicadoException(emailNormalizado);
        }
        Usuario usuario = new Usuario(UUID.randomUUID(), nombre, emailNormalizado,
                passwordEncoder.encode(password), rol, true, Instant.now());
        return usuarios.save(usuario);
    }

    public Page<Usuario> listar(Rol rol, Pageable pageable) {
        return rol == null ? usuarios.findAll(pageable) : usuarios.findByRol(rol, pageable);
    }

    public Usuario obtener(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new UsuarioNoEncontradoException(id));
    }
}
