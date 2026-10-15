package com.crediya.solicitudes.api.security;

import com.crediya.solicitudes.api.dto.response.ErrorResponse;
import com.crediya.solicitudes.api.exception.ErrorCatalog;
import com.crediya.solicitudes.model.common.exceptions.AccesoDenegadoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Responde 401 y 403 con el mismo formato JSON que el resto de errores de la API, sin revelar por que fallo la
 * autenticacion (token ausente, vencido, manipulado...).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorHandlers implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        log.warn("Acceso no autenticado a {}", exchange.getRequest().getPath().value());
        return escribir(exchange, new ErrorResponse(ErrorCatalog.CODIGO_NO_AUTENTICADO, ErrorCatalog.MENSAJE_NO_AUTENTICADO,
                HttpStatus.UNAUTHORIZED.value(), Instant.now(), exchange.getRequest().getPath().value(), null));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange,
                             org.springframework.security.access.AccessDeniedException denied) {
        log.warn("Acceso denegado a {}", exchange.getRequest().getPath().value());
        var dominio = new AccesoDenegadoException();
        return escribir(exchange, new ErrorResponse(dominio.getCode(), dominio.getMessage(),
                HttpStatus.FORBIDDEN.value(), Instant.now(), exchange.getRequest().getPath().value(), null));
    }

    private Mono<Void> escribir(ServerWebExchange exchange, ErrorResponse cuerpo) {
        var respuesta = exchange.getResponse();
        respuesta.setStatusCode(HttpStatus.valueOf(cuerpo.estado()));
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(cuerpo);
            return respuesta.writeWith(Mono.just(respuesta.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            byte[] respaldo = ("{\"codigo\":\"" + cuerpo.codigo() + "\"}").getBytes(StandardCharsets.UTF_8);
            return respuesta.writeWith(Mono.just(respuesta.bufferFactory().wrap(respaldo)));
        }
    }
}
