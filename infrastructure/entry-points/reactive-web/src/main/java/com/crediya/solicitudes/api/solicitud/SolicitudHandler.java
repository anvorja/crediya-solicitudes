package com.crediya.solicitudes.api.solicitud;

import com.crediya.solicitudes.api.constants.ValidationMessages;
import com.crediya.solicitudes.api.dto.request.CrearSolicitudRequest;
import com.crediya.solicitudes.api.mapper.SolicitudRestMapper;
import com.crediya.solicitudes.api.validation.RequestValidationException;
import com.crediya.solicitudes.api.validation.RequestValidator;
import com.crediya.solicitudes.usecase.crearsolicitud.CrearSolicitudUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolicitudHandler {

    private final CrearSolicitudUseCase crearSolicitudUseCase;
    private final SolicitudRestMapper mapper;
    private final RequestValidator validator;

    /**
     * POST /api/v1/solicitud (HU2).
     */
    public Mono<ServerResponse> crearSolicitud(ServerRequest request) {
        return request.bodyToMono(CrearSolicitudRequest.class)
                .switchIfEmpty(Mono.error(() -> new RequestValidationException(
                        Map.of(ValidationMessages.CAMPO_CUERPO, ValidationMessages.CUERPO_REQUERIDO))))
                .flatMap(validator::validar)
                .doOnNext(body -> log.info("Peticion de nueva solicitud de prestamo recibida"))
                .map(mapper::toCommand)
                .flatMap(crearSolicitudUseCase::ejecutar)
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .doOnSuccess(respuesta -> log.info("Solicitud de prestamo registrada"));
    }
}
