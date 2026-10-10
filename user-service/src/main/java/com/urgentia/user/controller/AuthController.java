package com.urgentia.user.controller;

import com.urgentia.user.dto.LoginRequest;
import com.urgentia.user.dto.LoginResponse;
import com.urgentia.user.dto.LoginResponse.UsuarioLogin;
import com.urgentia.user.model.Usuario;
import com.urgentia.user.service.AuthService;
import com.urgentia.user.service.AuthService.Login;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auth")
public class AuthController {

    private static final String EJEMPLO_LOGIN = """
            {"accessToken":"eyJhbGciOiJIUzI1NiJ9...","tokenType":"Bearer","expiresIn":3600,\
            "usuario":{"id":"b1c2d3e4-0000-4000-8000-000000000004","nombre":"Sofía Solicitante",\
            "email":"solicitante@urgentia.local","rol":"SOLICITANTE"}}""";
    private static final String EJEMPLO_CREDENCIALES_INVALIDAS = """
            {"codigo":"CREDENCIALES_INVALIDAS","mensaje":"Email o contraseña invalidos",\
            "timestamp":"2026-10-05T14:03:11Z","path":"/api/auth/login",\
            "correlationId":"a8e1b2c3-0000-4000-8000-000000000000"}""";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/login")
    @Operation(summary = "Iniciar sesion",
            description = "Email inexistente, contraseña incorrecta o usuario inactivo devuelven siempre el mismo "
                    + "401 CREDENCIALES_INVALIDAS.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Login correcto",
            content = @Content(examples = @ExampleObject(value = EJEMPLO_LOGIN)))
    @ApiResponse(responseCode = "401", description = "CREDENCIALES_INVALIDAS",
            content = @Content(schema = @Schema(implementation = com.urgentia.user.dto.ErrorResponse.class),
                    examples = @ExampleObject(value = EJEMPLO_CREDENCIALES_INVALIDAS)))
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Login login = authService.login(request.email(), request.password());
        Usuario usuario = login.usuario();
        return new LoginResponse(login.accessToken(), "Bearer", login.expiresIn(),
                new UsuarioLogin(usuario.getId().toString(), usuario.getNombre(), usuario.getEmail(),
                        usuario.getRol().name()));
    }
}
