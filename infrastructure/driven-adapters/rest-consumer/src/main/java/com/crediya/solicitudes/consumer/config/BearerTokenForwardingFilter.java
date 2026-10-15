package com.crediya.solicitudes.consumer.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Reenvia a AUTENTICACION el mismo token con el que el cliente llamo a SOLICITUDES, de modo que AUTENTICACION aplique sus
 * propias reglas (un CLIENTE solo puede consultar sus datos). Sin sesion no agrega ninguna credencial.
 */
public class BearerTokenForwardingFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest peticion, ExchangeFunction siguiente) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .filter(Jwt.class::isInstance)
                .map(principal -> ((Jwt) principal).getTokenValue())
                .map(token -> ClientRequest.from(peticion).headers(cabeceras -> cabeceras.setBearerAuth(token)).build())
                .defaultIfEmpty(peticion)
                .flatMap(siguiente::exchange);
    }
}
