package com.crediya.solicitudes.api.security;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtDecoderConfig {

    private static final int TIMEOUT_CONEXION_MS = 3000;
    private static final Duration TIMEOUT_RESPUESTA = Duration.ofSeconds(3);

    /**
     * Valida firma RS256 (con las claves publicas de AUTENTICACION, obtenidas del JWKS y cacheadas), vigencia, emisor y
     * audiencia. La descarga del JWKS es reactiva y tiene timeouts.
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties propiedades) {
        var cliente = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_CONEXION_MS)
                        .responseTimeout(TIMEOUT_RESPUESTA)))
                .build();
        var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(propiedades.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .webClient(cliente)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(propiedades.issuer()),
                audiencia(propiedades.audience())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audiencia(String esperada) {
        var error = new OAuth2Error("invalid_token", "El token no esta dirigido a este servicio", null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(esperada)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
