// infrastructure/entry-points/reactive-web/src/main/java/com/crediya/solicitudes/api/Handler.java
package com.crediya.solicitudes.api;

import com.crediya.solicitudes.api.dto.request.CrearSolicitudRequest;
import com.crediya.solicitudes.api.mapper.SolicitudRestMapper;
import com.crediya.solicitudes.model.solicitud.Solicitud;
import com.crediya.solicitudes.model.solicitud.exception.DatosSolicitudInvalidosException;
import com.crediya.solicitudes.model.solicitud.exception.SolicitudDuplicadaException;
import com.crediya.solicitudes.model.solicitud.exception.TipoPrestamoNoValidoException; // AGREGAR esta excepción
import com.crediya.solicitudes.usecase.crearsolicitud.CrearSolicitudUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Handler REACTIVO para Router Functions (WebFlux)
 * HU2: Registrar una solicitud de préstamo de forma completamente reactiva
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Handler {

    private final CrearSolicitudUseCase crearSolicitudUseCase;
    private final SolicitudRestMapper mapper;

    /**
     * POST /api/v1/solicitud
     * HU2: Como cliente, quiero enviar mi solicitud de préstamo con la información necesaria
     * COMPLETAMENTE REACTIVO - aprovecha las ventajas de WebFlux
     */
    public Mono<ServerResponse> crearSolicitud(ServerRequest request) {
        log.info("🚀 Handler - Iniciando proceso reactivo de creación de solicitud");

        return request.bodyToMono(CrearSolicitudRequest.class)
                .doOnNext(req -> log.info("📄 Solicitud recibida para documento: {}", req.numeroDocumento()))
                .flatMap(this::convertirYValidarRequest)
                .flatMap(crearSolicitudUseCase::ejecutar) // ¡UseCase reactivo!
                .flatMap(this::crearRespuestaExitosa)
                .onErrorResume(this::manejarErrores)
                .doOnTerminate(() -> log.info("✅ Proceso de creación de solicitud completado"));
    }

    /**
     * GET /api/v1/solicitud/{id}
     * Consultar solicitud por ID (placeholder para futuras HUs)
     */
    public Mono<ServerResponse> consultarSolicitud(ServerRequest request) {
        String id = request.pathVariable("id");
        log.info("🔍 Handler - Consultando solicitud: {}", id);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createPlaceholderResponse("Consulta por ID", "solicitudId", id));
    }

    /**
     * GET /api/v1/solicitud/documento/{numeroDocumento}
     * Consultar solicitud por documento (placeholder para futuras HUs)
     */
    public Mono<ServerResponse> consultarPorDocumento(ServerRequest request) {
        String numeroDocumento = request.pathVariable("numeroDocumento");
        log.info("🔍 Handler - Consultando por documento: {}", numeroDocumento);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createPlaceholderResponse("Consulta por documento", "numeroDocumento", numeroDocumento));
    }

    // ========================================
    // MÉTODOS PRIVADOS AUXILIARES
    // ========================================

    /**
     * Convierte el DTO request a entidad de dominio
     * MEJORA: Simplificado para evitar complejidad innecesaria
     */
    private Mono<Solicitud> convertirYValidarRequest(CrearSolicitudRequest request) {
        return Mono.just(request)
                .map(mapper::toModel)
                .doOnNext(solicitud -> log.debug("🔄 Solicitud mapeada: {} - Monto: ${:,.2f}",
                        solicitud.getNumeroDocumento(), solicitud.getMontoSolicitado()));
    }

    /**
     * Crea respuesta exitosa (HTTP 201 Created)
     * MANTIENE tu implementación actual - está bien
     */
    private Mono<ServerResponse> crearRespuestaExitosa(Solicitud solicitudCreada) {
        return Mono.fromCallable(() -> {
                    log.info("✅ Solicitud creada exitosamente: {} - Estado: {}",
                            solicitudCreada.getId(), solicitudCreada.getEstado());
                    return mapper.toResponse(solicitudCreada);
                })
                .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .doOnNext(serverResponse -> log.debug("📤 Respuesta HTTP 201 enviada"));
    }

    /**
     * Manejo de errores reactivo - convierte excepciones a respuestas HTTP apropiadas
     * MEJORA: Agregada excepción TipoPrestamoNoValidoException
     */
    private Mono<ServerResponse> manejarErrores(Throwable error) {
        log.error("❌ Error procesando solicitud: {} - Tipo: {}", error.getMessage(), error.getClass().getSimpleName());

        // MEJORA: Manejo específico por tipo de excepción del dominio
        return switch (error) {
            case SolicitudDuplicadaException ex ->
                    crearRespuestaError(HttpStatus.CONFLICT, "SOLICITUD_DUPLICADA", ex.getMessage());

            case DatosSolicitudInvalidosException ex ->
                    crearRespuestaError(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", ex.getMessage());

            case TipoPrestamoNoValidoException ex -> // NUEVO
                    crearRespuestaError(HttpStatus.BAD_REQUEST, "TIPO_PRESTAMO_INVALIDO", ex.getMessage());

            case IllegalArgumentException ex ->
                    crearRespuestaError(HttpStatus.BAD_REQUEST, "ARGUMENTOS_INVALIDOS", ex.getMessage());

            default ->
                    crearRespuestaError(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                            "Ha ocurrido un error inesperado. Por favor contacte al administrador.");
        };
    }

    /**
     * Crea respuesta de error estandarizada
     * MANTIENE tu implementación - está bien
     */
    private Mono<ServerResponse> crearRespuestaError(HttpStatus status, String codigo, String mensaje) {
        ErrorResponse errorResponse = new ErrorResponse(
                codigo,
                mensaje,
                status.value(),
                java.time.LocalDateTime.now()
        );

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorResponse)
                .doOnNext(response -> log.warn("📤 Respuesta de error enviada: {} - {}", status, codigo));
    }

    /**
     * Crea respuesta placeholder para endpoints en construcción
     * MANTIENE tu implementación - está bien
     */
    private PlaceholderResponse createPlaceholderResponse(String tipo, String campo, String valor) {
        return new PlaceholderResponse(
                "Endpoint en construcción - " + tipo,
                campo,
                valor,
                java.time.LocalDateTime.now()
        );
    }

    // ========================================
    // CLASES AUXILIARES PARA RESPUESTAS
    // ========================================

    /**
     * DTO para respuestas de error estandarizadas
     * MANTIENE tu implementación - está bien
     */
    public record ErrorResponse(
            String codigo,
            String mensaje,
            int status,
            java.time.LocalDateTime timestamp
    ) {}

    /**
     * DTO para respuestas placeholder
     * MANTIENE tu implementación - está bien
     */
    public record PlaceholderResponse(
            String message,
            String campo,
            String valor,
            java.time.LocalDateTime timestamp
    ) {}
}