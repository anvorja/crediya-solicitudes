package com.crediya.solicitudes.api.solicitud;

import com.crediya.solicitudes.api.constants.ApiRoutes;
import com.crediya.solicitudes.api.constants.ValidationMessages;
import com.crediya.solicitudes.api.dto.request.ActualizarEstadoRequest;
import com.crediya.solicitudes.api.dto.request.CrearSolicitudRequest;
import com.crediya.solicitudes.api.mapper.SolicitudRestMapper;
import com.crediya.solicitudes.api.validation.RequestValidationException;
import com.crediya.solicitudes.api.validation.RequestValidator;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;
import com.crediya.solicitudes.model.solicitud.queries.FiltroSolicitudes;
import com.crediya.solicitudes.usecase.actualizarestado.ActualizarEstadoSolicitudUseCase;
import com.crediya.solicitudes.usecase.crearsolicitud.CrearSolicitudUseCase;
import com.crediya.solicitudes.usecase.listarsolicitudes.ListarSolicitudesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolicitudHandler {

    private final CrearSolicitudUseCase crearSolicitudUseCase;
    private final ListarSolicitudesUseCase listarSolicitudesUseCase;
    private final ActualizarEstadoSolicitudUseCase actualizarEstadoSolicitudUseCase;
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

    /**
     * PUT /api/v1/solicitud (HU6): un asesor aprueba o rechaza una solicitud.
     */
    public Mono<ServerResponse> actualizarEstado(ServerRequest request) {
        return request.bodyToMono(ActualizarEstadoRequest.class)
                .switchIfEmpty(Mono.error(() -> new RequestValidationException(
                        Map.of(ValidationMessages.CAMPO_CUERPO, ValidationMessages.CUERPO_REQUERIDO))))
                .flatMap(validator::validar)
                .doOnNext(body -> log.info("Peticion de cambio de estado de solicitud recibida"))
                .map(mapper::toCommand)
                .flatMap(actualizarEstadoSolicitudUseCase::ejecutar)
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(response));
    }

    /**
     * GET /api/v1/solicitud (HU4): solicitudes pendientes de decision, paginadas y filtrables por estado y tipo.
     */
    public Mono<ServerResponse> listarSolicitudes(ServerRequest request) {
        return Mono.fromCallable(() -> filtroDe(request))
                .doOnNext(filtro -> log.info("Listado de solicitudes para revision pagina={} tamano={}", filtro.pagina(), filtro.tamano()))
                .flatMap(listarSolicitudesUseCase::ejecutar)
                .map(mapper::toPaginaResponse)
                .flatMap(pagina -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(pagina));
    }

    private static FiltroSolicitudes filtroDe(ServerRequest request) {
        List<EstadoSolicitud> estados = request.queryParams().getOrDefault(ApiRoutes.PARAM_ESTADO, List.of()).stream()
                .flatMap(valor -> Arrays.stream(valor.split(",")))
                .filter(texto -> !texto.isBlank())
                .map(EstadoSolicitud::desde)
                .toList();
        Map<String, String> errores = new TreeMap<>();
        Integer pagina = entero(request, ApiRoutes.PARAM_PAGINA, errores);
        Integer tamano = entero(request, ApiRoutes.PARAM_TAMANO, errores);
        if (!errores.isEmpty()) {
            throw new RequestValidationException(errores);
        }
        return FiltroSolicitudes.de(estados, request.queryParam(ApiRoutes.PARAM_TIPO_PRESTAMO).orElse(null), pagina, tamano);
    }

    private static Integer entero(ServerRequest request, String parametro, Map<String, String> errores) {
        try {
            return request.queryParam(parametro).map(String::trim).map(Integer::valueOf).orElse(null);
        } catch (NumberFormatException e) {
            errores.put(parametro, ValidationMessages.ENTERO_INVALIDO);
            return null;
        }
    }
}
