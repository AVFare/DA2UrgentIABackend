package com.urgentia.ticket.infrastructure.rest;

import com.urgentia.ticket.application.exception.DatosInvalidosException;
import com.urgentia.ticket.application.port.in.AsignarTicketUseCase;
import com.urgentia.ticket.application.port.in.CambiarEstadoUseCase;
import com.urgentia.ticket.application.port.in.ConsultarTicketsUseCase;
import com.urgentia.ticket.application.port.in.CrearTicketUseCase;
import com.urgentia.ticket.application.port.in.CrearTicketUseCase.CrearTicketCommand;
import com.urgentia.ticket.application.port.in.ReclasificarTicketUseCase;
import com.urgentia.ticket.application.port.in.Rol;
import com.urgentia.ticket.application.port.in.UsuarioActual;
import com.urgentia.ticket.application.port.out.FiltroTickets;
import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Prioridad;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.infrastructure.rest.dto.AsignarTicketRequest;
import com.urgentia.ticket.infrastructure.rest.dto.CambiarEstadoRequest;
import com.urgentia.ticket.infrastructure.rest.dto.CrearTicketRequest;
import com.urgentia.ticket.infrastructure.rest.dto.ErrorResponse;
import com.urgentia.ticket.infrastructure.rest.dto.PaginaResponse;
import com.urgentia.ticket.infrastructure.rest.dto.TicketResponse;
import com.urgentia.ticket.infrastructure.rest.mapper.TicketRestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador REST (puerto de entrada HTTP). Traduce HTTP a casos de uso y el agregado
 * al JSON del contrato. No tiene reglas de negocio.
 *
 * <p>X-User-Id y X-User-Rol los pone el gateway a partir del JWT; por eso no se muestran
 * en el Swagger. Para llamar al servicio directo (sin gateway) hay que mandarlos a mano.
 */
@RestController
@RequestMapping(path = "/api/tickets", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tickets", description = "Alta, consulta, asignacion, estados y reclasificacion de tickets")
public class TicketController {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROL = "X-User-Rol";
    static final int TAMANIO_MAXIMO = 100;

    private static final String EJEMPLO_TRANSICION = """
            {"codigo":"TRANSICION_INVALIDA","mensaje":"No se puede pasar de CERRADO a EN_CURSO",\
            "detalles":[{"campo":"estado","mensaje":"transición no permitida"}],"timestamp":"2026-10-05T14:03:11Z",\
            "path":"/api/tickets/7c9e6679-7425-40de-944b-e07fc1f90ae7/estado","correlationId":"a8e1b2c3-0000-4000-8000-000000000000"}""";
    private static final String EJEMPLO_REGLA = """
            {"codigo":"REGLA_DE_NEGOCIO","mensaje":"Para pasar a EN_CURSO el ticket tiene que tener un agente asignado",\
            "timestamp":"2026-10-05T14:03:11Z","path":"/api/tickets/7c9e6679-7425-40de-944b-e07fc1f90ae7/estado",\
            "correlationId":"a8e1b2c3-0000-4000-8000-000000000000"}""";
    private static final String EJEMPLO_IA = """
            {"codigo":"IA_NO_DISPONIBLE","mensaje":"La IA no esta disponible: el ticket no se reclasifico",\
            "timestamp":"2026-10-05T14:03:11Z","path":"/api/tickets/7c9e6679-7425-40de-944b-e07fc1f90ae7/reclasificacion",\
            "correlationId":"a8e1b2c3-0000-4000-8000-000000000000"}""";

    private final CrearTicketUseCase crear;
    private final ConsultarTicketsUseCase consultar;
    private final AsignarTicketUseCase asignar;
    private final CambiarEstadoUseCase cambiarEstado;
    private final ReclasificarTicketUseCase reclasificar;

    public TicketController(CrearTicketUseCase crear, ConsultarTicketsUseCase consultar, AsignarTicketUseCase asignar,
                            CambiarEstadoUseCase cambiarEstado, ReclasificarTicketUseCase reclasificar) {
        this.crear = crear;
        this.consultar = consultar;
        this.asignar = asignar;
        this.cambiarEstado = cambiarEstado;
        this.reclasificar = reclasificar;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear un ticket",
            description = "Crea el ticket, lo clasifica con la IA, calcula prioridad y SLA y, si es critico, lo escala. "
                    + "Si la IA falla o tarda mas de 7 s, el ticket queda PENDIENTE_CLASIFICACION y se responde 201 igual.")
    @ApiResponse(responseCode = "201", description = "Ticket creado",
            content = @Content(schema = @Schema(implementation = TicketResponse.class)))
    @ApiResponse(responseCode = "400", description = "VALIDACION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TicketResponse> crear(
            @Parameter(hidden = true) @RequestHeader(value = HEADER_USER_ID, required = false) String usuarioId,
            @Valid @RequestBody CrearTicketRequest pedido) {
        UUID solicitante = uuidObligatorio(usuarioId);
        Ticket ticket = crear.crear(new CrearTicketCommand(pedido.titulo(), pedido.descripcion(), solicitante));
        return ResponseEntity.created(URI.create("/api/tickets/" + ticket.id())).body(TicketRestMapper.aResponse(ticket));
    }

    @GetMapping
    @Operation(summary = "Listar tickets",
            description = "Orden: prioridad ascendente (P1 primero; sin prioridad al final) y fecha de creacion "
                    + "descendente. Un SOLICITANTE solo ve sus propios tickets.")
    @ApiResponse(responseCode = "200", description = "Pagina de tickets")
    @ApiResponse(responseCode = "400", description = "VALIDACION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PaginaResponse<TicketResponse> listar(
            @Parameter(hidden = true) @RequestHeader(value = HEADER_USER_ID, required = false) String usuarioId,
            @Parameter(hidden = true) @RequestHeader(value = HEADER_USER_ROL, required = false) String rol,
            @RequestParam(required = false) EstadoTicket estado,
            @RequestParam(required = false) Prioridad prioridad,
            @RequestParam(required = false) Categoria categoria,
            @Parameter(description = "Numero de pagina, desde 0") @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "debe ser 0 o mayor") int page,
            @Parameter(description = "Tamanio de pagina (maximo 100)") @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe ser 1 o mayor") @Max(value = TAMANIO_MAXIMO, message = "puede ser hasta 100")
            int size) {
        if (page < 0) {
            throw new DatosInvalidosException("page", "debe ser 0 o mayor");
        }
        if (size < 1 || size > TAMANIO_MAXIMO) {
            throw new DatosInvalidosException("size", "debe estar entre 1 y 100");
        }
        FiltroTickets filtro = new FiltroTickets(estado, prioridad, categoria, null);
        return TicketRestMapper.aResponse(consultar.buscar(filtro, usuarioActual(usuarioId, rol), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un ticket", description = "Un SOLICITANTE solo puede ver sus propios tickets (si no, 404).")
    @ApiResponse(responseCode = "200", description = "El ticket")
    @ApiResponse(responseCode = "400", description = "VALIDACION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TicketResponse obtener(
            @Parameter(hidden = true) @RequestHeader(value = HEADER_USER_ID, required = false) String usuarioId,
            @Parameter(hidden = true) @RequestHeader(value = HEADER_USER_ROL, required = false) String rol,
            @PathVariable UUID id) {
        return TicketRestMapper.aResponse(consultar.obtener(TicketId.de(id), usuarioActual(usuarioId, rol)));
    }

    @PatchMapping(path = "/{id}/asignacion", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Asignar un agente",
            description = "En CLASIFICADO pasa a ASIGNADO. En ESCALADO asigna (o reasigna) sin cambiar el estado.")
    @ApiResponse(responseCode = "200", description = "Ticket asignado")
    @ApiResponse(responseCode = "400", description = "VALIDACION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "TRANSICION_INVALIDA",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = EJEMPLO_TRANSICION)))
    public TicketResponse asignar(@PathVariable UUID id, @Valid @RequestBody AsignarTicketRequest pedido) {
        return TicketRestMapper.aResponse(asignar.asignar(TicketId.de(id), pedido.agenteId()));
    }

    @PatchMapping(path = "/{id}/estado", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cambiar el estado",
            description = "Transiciones permitidas: ASIGNADO -> EN_CURSO o ESCALADO; EN_CURSO -> RESUELTO o ESCALADO; "
                    + "ESCALADO -> EN_CURSO (requiere agente); RESUELTO -> CERRADO o EN_CURSO. "
                    + "El motivo es obligatorio solo para ESCALADO.")
    @ApiResponse(responseCode = "200", description = "Estado cambiado")
    @ApiResponse(responseCode = "400", description = "VALIDACION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "TRANSICION_INVALIDA o REGLA_DE_NEGOCIO",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                    @ExampleObject(name = "TRANSICION_INVALIDA", value = EJEMPLO_TRANSICION),
                    @ExampleObject(name = "REGLA_DE_NEGOCIO", value = EJEMPLO_REGLA)}))
    public TicketResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambiarEstadoRequest pedido) {
        if (pedido.estado() == EstadoTicket.ESCALADO && (pedido.motivo() == null || pedido.motivo().isBlank())) {
            throw new DatosInvalidosException("motivo", "es obligatorio para pasar a ESCALADO");
        }
        return TicketRestMapper.aResponse(cambiarEstado.cambiarEstado(TicketId.de(id), pedido.estado(), pedido.motivo()));
    }

    @PostMapping("/{id}/reclasificacion")
    @Operation(summary = "Reclasificar con la IA",
            description = "Solo para tickets PENDIENTE_CLASIFICACION o CLASIFICADO. Recalcula prioridad y SLA y puede "
                    + "escalar. Si la IA no responde, 503 y el ticket no cambia.")
    @ApiResponse(responseCode = "200", description = "Ticket reclasificado")
    @ApiResponse(responseCode = "400", description = "VALIDACION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "NO_ENCONTRADO",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "TRANSICION_INVALIDA",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "IA_NO_DISPONIBLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = EJEMPLO_IA)))
    public TicketResponse reclasificar(@PathVariable UUID id) {
        return TicketRestMapper.aResponse(reclasificar.reclasificar(TicketId.de(id)));
    }

    private static UUID uuidObligatorio(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new DatosInvalidosException(HEADER_USER_ID, "falta el header (lo pone el gateway a partir del token)");
        }
        return uuid(valor);
    }

    private static UUID uuid(String valor) {
        try {
            return UUID.fromString(valor.strip());
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException(HEADER_USER_ID, "no es un UUID valido");
        }
    }

    /** Sin headers (llamada interna, sin gateway) no se restringe nada. */
    private static UsuarioActual usuarioActual(String usuarioId, String rol) {
        UUID id = (usuarioId == null || usuarioId.isBlank()) ? null : uuid(usuarioId);
        Rol rolActual = null;
        if (rol != null && !rol.isBlank()) {
            try {
                rolActual = Rol.valueOf(rol.strip());
            } catch (IllegalArgumentException e) {
                throw new DatosInvalidosException(HEADER_USER_ROL, "rol desconocido: " + rol);
            }
        }
        return new UsuarioActual(id, rolActual);
    }
}
