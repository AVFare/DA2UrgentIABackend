package com.urgentia.user.controller;

import com.urgentia.user.dto.CrearUsuarioRequest;
import com.urgentia.user.dto.ErrorResponse;
import com.urgentia.user.dto.PaginaResponse;
import com.urgentia.user.dto.UsuarioResponse;
import com.urgentia.user.model.Rol;
import com.urgentia.user.model.Usuario;
import com.urgentia.user.service.DatosInvalidosException;
import com.urgentia.user.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El gateway es el unico que controla los roles (POST solo ADMIN, GET solo AGENTE/ADMIN,
 * seccion 6.1 del contexto); este controller no vuelve a chequearlos.
 */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios")
public class UsuarioController {

    static final int TAMANIO_MAXIMO = 100;

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    @Operation(summary = "Crear un usuario",
            description = "El email se normaliza a minusculas; si ya existe, 409 EMAIL_DUPLICADO.")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "409", description = "EMAIL_DUPLICADO",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        Usuario usuario = usuarioService.crear(request.nombre(), request.email(), request.password(), request.rol());
        return ResponseEntity.created(URI.create("/api/usuarios/" + usuario.getId()))
                .body(UsuarioResponse.de(usuario));
    }

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Filtro opcional por rol. Tamaño de pagina: hasta 100.")
    public PaginaResponse<UsuarioResponse> listar(
            @RequestParam(required = false) Rol rol,
            @Parameter(description = "Numero de pagina, desde 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de pagina (maximo 100)") @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new DatosInvalidosException("page", "debe ser 0 o mayor");
        }
        if (size < 1 || size > TAMANIO_MAXIMO) {
            throw new DatosInvalidosException("size", "debe estar entre 1 y " + TAMANIO_MAXIMO);
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Usuario> pagina = usuarioService.listar(rol, pageable);
        return new PaginaResponse<>(pagina.getContent().stream().map(UsuarioResponse::de).toList(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un usuario")
    @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UsuarioResponse obtener(@PathVariable UUID id) {
        return UsuarioResponse.de(usuarioService.obtener(id));
    }
}
