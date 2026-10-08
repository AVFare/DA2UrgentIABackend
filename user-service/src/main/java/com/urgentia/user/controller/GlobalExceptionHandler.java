package com.urgentia.user.controller;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.urgentia.user.config.CorrelationIdFilter;
import com.urgentia.user.dto.ErrorResponse;
import com.urgentia.user.dto.ErrorResponse.Detalle;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convierte toda excepcion en el formato comun de error, con el correlationId
 * del pedido. Nunca devuelve el stacktrace: los errores inesperados se loguean y se responde 500.
 *
 * <p>Las excepciones de negocio propias de user-service (CREDENCIALES_INVALIDAS, NO_ENCONTRADO,
 * EMAIL_DUPLICADO) se agregan en el Paso 3, junto con los casos de uso que las lanzan.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> inesperado(Exception e, HttpServletRequest pedido) {
        log.error("Error inesperado en {} {}", pedido.getMethod(), ruta(pedido), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Error interno del servidor", null,
                ruta(pedido), null);
    }

    // ------------------------------------------------------------------ errores de Spring MVC

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Detalle> detalles = new ArrayList<>();
        for (FieldError campo : ex.getBindingResult().getFieldErrors()) {
            detalles.add(new Detalle(campo.getField(), campo.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(global -> detalles.add(new Detalle(global.getObjectName(), global.getDefaultMessage())));
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "El pedido tiene datos invalidos", detalles,
                ruta(request), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<Detalle> detalles = new ArrayList<>();
        for (ParameterValidationResult resultado : ex.getValueResults()) {
            String parametro = resultado.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : resultado.getResolvableErrors()) {
                detalles.add(new Detalle(parametro, error.getDefaultMessage()));
            }
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "El pedido tiene datos invalidos", detalles,
                ruta(request), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Detalle> detalles = null;
        if (ex.getCause() instanceof JsonMappingException mapeo && !mapeo.getPath().isEmpty()) {
            String campo = mapeo.getPath().stream()
                    .map(referencia -> referencia.getFieldName() != null
                            ? referencia.getFieldName() : "[" + referencia.getIndex() + "]")
                    .collect(Collectors.joining("."));
            detalles = List.of(new Detalle(campo, mensajeDeFormato(mapeo)));
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "El cuerpo del pedido no es un JSON valido", detalles,
                ruta(request), headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String campo = ex instanceof MethodArgumentTypeMismatchException argumento
                ? argumento.getName() : ex.getPropertyName();
        String mensaje = "valor invalido: " + ex.getValue();
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            mensaje += " (valores posibles: " + valoresPosibles(ex.getRequiredType()) + ")";
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "El pedido tiene datos invalidos",
                List.of(new Detalle(campo, mensaje)), ruta(request), headers);
    }

    /** Cualquier otro error de Spring MVC (ruta inexistente, metodo no permitido, etc.). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String codigo;
        String mensaje;
        if (status == HttpStatus.NOT_FOUND) {
            codigo = "NO_ENCONTRADO";
            mensaje = "No existe el recurso pedido";
        } else if (status.is4xxClientError()) {
            codigo = "VALIDACION";
            mensaje = ex.getMessage();
        } else {
            log.error("Error interno en {}", ruta(request), ex);
            codigo = "ERROR_INTERNO";
            mensaje = "Error interno del servidor";
        }
        return error(status, codigo, mensaje, null, ruta(request), headers);
    }

    // ------------------------------------------------------------------ armado de la respuesta

    private static ResponseEntity<Object> error(HttpStatus status, String codigo, String mensaje,
                                                List<Detalle> detalles, String path, HttpHeaders headers) {
        ErrorResponse cuerpo = new ErrorResponse(codigo, mensaje, detalles,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), path, MDC.get(CorrelationIdFilter.MDC_CLAVE));
        HttpHeaders respuesta = new HttpHeaders();
        if (headers != null) {
            respuesta.putAll(headers);
        }
        respuesta.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(cuerpo, respuesta, status);
    }

    private static String ruta(HttpServletRequest pedido) {
        return pedido.getRequestURI();
    }

    private static String ruta(WebRequest request) {
        if (request instanceof ServletWebRequest servlet) {
            return servlet.getRequest().getRequestURI();
        }
        return request.getDescription(false).replaceFirst("^uri=", "");
    }

    private static String mensajeDeFormato(JsonMappingException e) {
        if (e instanceof InvalidFormatException formato) {
            String mensaje = "valor invalido: " + formato.getValue();
            if (formato.getTargetType() != null && formato.getTargetType().isEnum()) {
                mensaje += " (valores posibles: " + valoresPosibles(formato.getTargetType()) + ")";
            }
            return mensaje;
        }
        return "formato invalido";
    }

    private static String valoresPosibles(Class<?> tipoEnum) {
        return Arrays.stream(tipoEnum.getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "));
    }
}
