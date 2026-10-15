package com.crediya.solicitudes.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityErrorHandlers y SecurityConfig")
class SecurityErrorHandlersTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final SecurityErrorHandlers handlers = new SecurityErrorHandlers(mapper);

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/usuarios/1"));
    }

    @Test
    @DisplayName("cuando no hay autenticacion valida, debe responder 401 JSON sin revelar el motivo")
    void sinAutenticacion_debeResponder401() {
        var exchange = exchange();

        StepVerifier.create(handlers.commence(exchange, new BadCredentialsException("token expirado en 2020 firma invalida")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        String cuerpo = exchange.getResponse().getBodyAsString().block();
        assertThat(cuerpo).contains("NO_AUTENTICADO").contains("/api/v1/usuarios/1")
                .doesNotContain("expirado").doesNotContain("firma");
    }

    @Test
    @DisplayName("cuando el rol no alcanza, debe responder 403 JSON con el codigo del dominio")
    void sinPermisos_debeResponder403() {
        var exchange = exchange();

        StepVerifier.create(handlers.handle(exchange, new AccessDeniedException("falta ROLE_ADMIN"))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String cuerpo = exchange.getResponse().getBodyAsString().block();
        assertThat(cuerpo).contains("ACCESO_DENEGADO").doesNotContain("ROLE_ADMIN");
    }

    @Test
    @DisplayName("cuando el mapper falla al serializar, debe responder igual con un cuerpo minimo")
    void cuandoSerializacionFalla_debeResponderCuerpoMinimo() {
        // Sin JavaTimeModule, serializar el Instant del error falla y se usa el respaldo
        var sinModulo = new SecurityErrorHandlers(new ObjectMapper());
        var exchange = exchange();

        StepVerifier.create(sinModulo.commence(exchange, new BadCredentialsException("x"))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("NO_AUTENTICADO");
    }

    private static Jwt jwt(String rol) {
        var builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (rol != null) {
            builder.claim("rol", rol);
        }
        return builder.build();
    }

    @Test
    @DisplayName("el claim rol debe convertirse en la autoridad ROLE_{rol} en mayusculas")
    void claimRol_debeConvertirseEnAutoridad() {
        Flux<GrantedAuthority> autoridades = SecurityConfig.autoridadesDesdeRol().convert(jwt(" asesor "));

        StepVerifier.create(autoridades.map(GrantedAuthority::getAuthority)).expectNext("ROLE_ASESOR").verifyComplete();
    }

    @Test
    @DisplayName("cuando el token no trae rol, no debe otorgar ninguna autoridad")
    void sinRol_noDebeOtorgarAutoridades() {
        StepVerifier.create(SecurityConfig.autoridadesDesdeRol().convert(jwt(null))).verifyComplete();
        StepVerifier.create(SecurityConfig.autoridadesDesdeRol().convert(jwt("  "))).verifyComplete();
    }

    @Test
    @DisplayName("el convertidor de JWT debe producir una autenticacion con las autoridades del rol")
    void convertidor_debeProducirAutenticacionConAutoridades() {
        var autenticacion = SecurityConfig.convertidorDeJwt().convert(jwt("ADMIN")).block();

        assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .isEqualTo(List.of("ROLE_ADMIN"));
        assertThat(autenticacion.isAuthenticated()).isTrue();
    }
}
