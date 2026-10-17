package com.crediya.solicitudes.consumer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BearerTokenForwardingFilter")
class BearerTokenForwardingFilterTest {

    private final BearerTokenForwardingFilter filtro = new BearerTokenForwardingFilter();
    private final AtomicReference<ClientRequest> enviada = new AtomicReference<>();
    private final ExchangeFunction siguiente = peticion -> {
        enviada.set(peticion);
        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
    };

    private static ClientRequest peticion() {
        return ClientRequest.create(HttpMethod.GET, URI.create("http://auth/api/v1/usuarios/1")).build();
    }

    private static Jwt jwt(String valor) {
        return Jwt.withTokenValue(valor).header("alg", "RS256").subject("s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    @DisplayName("cuando hay un JWT en el contexto, debe reenviarlo como Authorization Bearer")
    void cuandoHayJwt_debeReenviarlo() {
        var autenticacion = new UsernamePasswordAuthenticationToken(jwt("token-del-cliente"), null, List.of());

        StepVerifier.create(filtro.filter(peticion(), siguiente)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(autenticacion)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(enviada.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-del-cliente");
    }

    @Test
    @DisplayName("cuando no hay contexto de seguridad, no debe agregar credenciales")
    void cuandoSinContexto_noDebeAgregarCredenciales() {
        StepVerifier.create(filtro.filter(peticion(), siguiente)).expectNextCount(1).verifyComplete();

        assertThat(enviada.get().headers().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    @DisplayName("cuando el principal no es un JWT, no debe agregar credenciales")
    void cuandoPrincipalNoEsJwt_noDebeAgregarCredenciales() {
        var autenticacion = new UsernamePasswordAuthenticationToken("usuario", "clave-en-claro", List.of());

        StepVerifier.create(filtro.filter(peticion(), siguiente)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(autenticacion)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(enviada.get().headers().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }
}
