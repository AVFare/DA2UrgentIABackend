package com.urgentia.user.service;

import com.urgentia.user.model.Usuario;
import com.urgentia.user.repository.UsuarioRepository;
import com.urgentia.user.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record Login(String accessToken, long expiresIn, Usuario usuario) {
    }

    /** Email inexistente, contraseña incorrecta o usuario inactivo dan siempre el mismo error. */
    public Login login(String email, String password) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(email)
                .filter(Usuario::isActivo)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(CredencialesInvalidasException::new);
        return new Login(jwtService.generar(usuario), jwtService.expiracionEnSegundos(), usuario);
    }
}
