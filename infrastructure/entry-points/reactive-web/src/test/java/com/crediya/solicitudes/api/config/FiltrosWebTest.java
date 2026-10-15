package com.crediya.solicitudes.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@DisplayName("Filtros web: cabeceras de seguridad y CORS")
class FiltrosWebTest {

    private static final String ORIGEN_PERMITIDO = "http://localhost:4200";

    private final WebTestClient client = WebTestClient
            .bindToRouterFunction(RouterFunctions.route(GET("/ping"), request -> ServerResponse.ok().bodyValue("pong")))
            .webFilter(new SecurityHeadersConfig(), new CorsWebFilter(new CorsConfig().corsConfigurationSource(ORIGEN_PERMITIDO)))
            .build();

    @Test
    @DisplayName("toda respuesta debe incluir las cabeceras de seguridad")
    void toda_respuesta_debeIncluirCabecerasDeSeguridad() {
        client.get().uri("/ping").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin")
                .expectHeader().exists("Strict-Transport-Security")
                .expectHeader().exists("Content-Security-Policy");
    }

    @Test
    @DisplayName("un origen permitido debe recibir la cabecera CORS")
    void origenPermitido_debeRecibirCors() {
        var resultado = client.get().uri("http://api.crediya.test/ping").header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO).exchange()
                .returnResult(String.class);
        org.assertj.core.api.Assertions.assertThat(resultado.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .as("cabeceras: %s", resultado.getResponseHeaders()).isEqualTo(ORIGEN_PERMITIDO);
    }

    @Test
    @DisplayName("un origen no permitido no debe recibir la cabecera CORS")
    void origenNoPermitido_noDebeRecibirCors() {
        client.options().uri("http://api.crediya.test/ping")
                .header(HttpHeaders.ORIGIN, "http://sitio-malicioso.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }
}
