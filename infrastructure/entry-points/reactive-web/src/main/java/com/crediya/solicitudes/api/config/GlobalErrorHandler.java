// infrastructure/entry-points/reactive-web/src/main/java/com/crediya/solicitudes/api/config/GlobalErrorHandler.java
package com.crediya.solicitudes.api.config;

import com.crediya.solicitudes.model.solicitud.exception.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exception.SolicitudDuplicadaException;
import com.crediya.solicitudes.model.solicitud.exception.TipoPrestamoNoValidoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejo global de errores para WebFlux (HU2)
 * CUMPLE: ErrorWebExceptionHandler para flujos reactivos
 * Convierte excepciones del dominio en respuestas HTTP uniformes
 */
@Component
@Order(-2) // Prioridad alta para interceptar errores antes del handler por defecto
@RequiredArgsConstructor
@Slf4j
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable ex) {

        var response = exchange.getResponse();
        var request = exchange.getRequest();

        // Determinar status code y mensaje según el tipo de excepción
        ErrorInfo errorInfo = determinarError(ex);

        // Log del error
        if (errorInfo.status().is5xxServerError()) {
            log.error("Error interno del servidor en {}: {}",
                    request.getPath(), ex.getMessage(), ex);
        } else {
            log.warn("Error de cliente en {}: {}",
                    request.getPath(), ex.getMessage());
        }

        // Configurar respuesta HTTP
        response.setStatusCode(errorInfo.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Crear cuerpo de respuesta
        ErrorResponse errorResponse = new ErrorResponse(
                errorInfo.code(),
                errorInfo.message(),
                errorInfo.status().value(),
                LocalDateTime.now(),
                request.getPath().value(),
                errorInfo.details()
        );

        try {
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(jsonResponse.getBytes());
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializando respuesta de error", e);
            return response.setComplete();
        }
    }

    /**
     * Determina el tipo de error y status code según la excepción
     * CUMPLE: Excepciones específicas del dominio, no genéricas
     */
    private ErrorInfo determinarError(Throwable ex) {
        return switch (ex) {

            // ===== ERRORES DEL DOMINIO (400 BAD REQUEST) =====
            case DatosSolicitudInvalidosException e -> new ErrorInfo(
                    HttpStatus.BAD_REQUEST,
                    "DATOS_SOLICITUD_INVALIDOS",
                    "Datos de solicitud inválidos: " + e.getMessage(),
                    null
            );

            case TipoPrestamoNoValidoException e -> new ErrorInfo(
                    HttpStatus.BAD_REQUEST,
                    "TIPO_PRESTAMO_INVALIDO",
                    "Tipo de préstamo no válido: " + e.getMessage(),
                    null
            );

            case SolicitudDuplicadaException e -> new ErrorInfo(
                    HttpStatus.CONFLICT,
                    "SOLICITUD_DUPLICADA",
                    "Cliente ya tiene una solicitud activa: " + e.getMessage(),
                    null
            );

            // ===== ERRORES DE VALIDACIÓN (400 BAD REQUEST) =====
            case WebExchangeBindException e -> new ErrorInfo(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACION_FALLIDA",
                    "Errores de validación en los datos de entrada",
                    extraerMensajesValidacion(e)
            );

            // ===== ERRORES TÉCNICOS (500 INTERNAL SERVER ERROR) =====
            case IllegalArgumentException e -> new ErrorInfo(
                    HttpStatus.BAD_REQUEST,
                    "ARGUMENTO_INVALIDO",
                    "Argumento inválido: " + e.getMessage(),
                    null
            );

            // ===== ERROR GENÉRICO (500 INTERNAL SERVER ERROR) =====
            default -> new ErrorInfo(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ERROR_INTERNO",
                    "Error interno del servidor. Consulte los logs para más detalles.",
                    null
            );
        };
    }

    /**
     * Extrae mensajes de validación de WebExchangeBindException
     */
    private Map<String, String> extraerMensajesValidacion(WebExchangeBindException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errores.put(error.getField(), error.getDefaultMessage()));
        return errores;
    }

    /**
     * Record para información de error interno
     */
    private record ErrorInfo(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> details
    ) {}

    /**
     * DTO para respuesta de error JSON - Compatible con tu ErrorResponse existente
     */
    public record ErrorResponse(
            String error,
            String message,
            int status,
            LocalDateTime timestamp,
            String path,
            Map<String, String> details
    ) {}
}