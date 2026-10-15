package com.crediya.solicitudes.api.exception;

import com.crediya.solicitudes.api.dto.response.ErrorResponse;
import com.crediya.solicitudes.api.validation.RequestValidationException;
import com.crediya.solicitudes.model.common.exceptions.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Centraliza la conversion de excepciones en respuestas HTTP uniformes. Ningun detalle interno llega al cliente.
 */
@Slf4j
@Component
@Order(-2)
public class GlobalErrorHandler extends AbstractErrorWebExceptionHandler {

    public GlobalErrorHandler(ErrorAttributes errorAttributes,
                              WebProperties webProperties,
                              ApplicationContext applicationContext,
                              ServerCodecConfigurer codecConfigurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageWriters(codecConfigurer.getWriters());
        setMessageReaders(codecConfigurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderizarError);
    }

    private Mono<ServerResponse> renderizarError(ServerRequest request) {
        Throwable error = getError(request);
        ErrorResponse cuerpo = construirRespuesta(error, request.path());
        registrar(error, cuerpo);
        return ServerResponse.status(cuerpo.estado())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo);
    }

    private ErrorResponse construirRespuesta(Throwable error, String ruta) {
        if (error instanceof DomainException dominio) {
            return respuesta(ErrorCatalog.estadoDe(dominio.getCode()), dominio.getCode(), dominio.getMessage(), ruta, null);
        }
        if (error instanceof RequestValidationException validacion) {
            return respuesta(HttpStatus.BAD_REQUEST, ErrorCatalog.CODIGO_VALIDACION, validacion.getMessage(), ruta,
                    validacion.getDetalles());
        }
        if (error instanceof ResponseStatusException http) {
            return respuestaHttp(http.getStatusCode(), ruta);
        }
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCatalog.CODIGO_ERROR_INTERNO,
                ErrorCatalog.MENSAJE_ERROR_INTERNO, ruta, null);
    }

    private ErrorResponse respuestaHttp(HttpStatusCode estado, String ruta) {
        if (estado.is5xxServerError()) {
            return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCatalog.CODIGO_ERROR_INTERNO,
                    ErrorCatalog.MENSAJE_ERROR_INTERNO, ruta, null);
        }
        return new ErrorResponse(ErrorCatalog.CODIGO_PETICION_INVALIDA,
                "La peticion no es valida o no esta soportada", estado.value(), Instant.now(), ruta, null);
    }

    private ErrorResponse respuesta(HttpStatus estado, String codigo, String mensaje, String ruta,
                                    Map<String, String> detalles) {
        return new ErrorResponse(codigo, mensaje, estado.value(), Instant.now(), ruta, detalles);
    }

    private void registrar(Throwable error, ErrorResponse cuerpo) {
        if (cuerpo.estado() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Error interno en {}", cuerpo.ruta(), error);
        } else {
            log.warn("Peticion rechazada en {}: {} ({})", cuerpo.ruta(), cuerpo.codigo(), cuerpo.estado());
        }
    }
}
