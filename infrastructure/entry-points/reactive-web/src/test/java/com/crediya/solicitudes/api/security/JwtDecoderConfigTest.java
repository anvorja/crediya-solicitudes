package com.crediya.solicitudes.api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtDecoderConfig: validacion de tokens de AUTENTICACION con su JWKS")
class JwtDecoderConfigTest {

    private MockWebServer jwks;
    private RSAKey claveDeAutenticacion;
    private ReactiveJwtDecoder decoder;

    @BeforeEach
    void iniciar() throws Exception {
        claveDeAutenticacion = new RSAKeyGenerator(2048).keyID("k1").generate();
        jwks = new MockWebServer();
        jwks.start();
        jwks.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody(new JWKSet(claveDeAutenticacion.toPublicJWK()).toString()));
        var propiedades = new JwtProperties(jwks.url("/.well-known/jwks.json").toString(), null, null);
        decoder = new JwtDecoderConfig().reactiveJwtDecoder(propiedades);
    }

    @AfterEach
    void detener() throws IOException {
        jwks.shutdown();
    }

    private String token(RSAKey firma, String emisor, String audiencia, long vigenciaMs) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(emisor).audience(audiencia).subject(UUID.randomUUID().toString())
                .claim("rol", "CLIENTE").claim("documento_identidad", "1020304050")
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + vigenciaMs)).build();
        var firmado = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(firma.getKeyID()).build(), claims);
        firmado.sign(new RSASSASigner(firma));
        return firmado.serialize();
    }

    @Test
    @DisplayName("debe aceptar un token firmado por AUTENTICACION y descargar el JWKS por HTTP")
    void debeAceptarTokenValido() throws Exception {
        StepVerifier.create(decoder.decode(token(claveDeAutenticacion, "crediya-auth", "crediya", 60_000)))
                .assertNext(jwt -> assertThat(jwt.<String>getClaim("rol")).isEqualTo("CLIENTE"))
                .verifyComplete();

        assertThat(jwks.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("debe rechazar un token firmado con una clave que no esta en el JWKS")
    void debeRechazarClaveAjena() throws Exception {
        var ajena = new RSAKeyGenerator(2048).keyID("k1").generate();

        StepVerifier.create(decoder.decode(token(ajena, "crediya-auth", "crediya", 60_000)))
                .expectError(BadJwtException.class)
                .verify();
    }

    @Test
    @DisplayName("debe rechazar un token vencido")
    void debeRechazarVencido() throws Exception {
        StepVerifier.create(decoder.decode(token(claveDeAutenticacion, "crediya-auth", "crediya", -120_000)))
                .expectError(BadJwtException.class)
                .verify();
    }

    @Test
    @DisplayName("debe rechazar un token de otro emisor o dirigido a otra audiencia")
    void debeRechazarEmisorOAudienciaAjenos() throws Exception {
        StepVerifier.create(decoder.decode(token(claveDeAutenticacion, "otro-emisor", "crediya", 60_000)))
                .expectError(BadJwtException.class).verify();
        StepVerifier.create(decoder.decode(token(claveDeAutenticacion, "crediya-auth", "otro-servicio", 60_000)))
                .expectError(BadJwtException.class).verify();
    }

    @Test
    @DisplayName("las propiedades deben tener emisor y audiencia por defecto")
    void propiedades_debenTenerDefaults() {
        var porDefecto = new JwtProperties("http://auth/jwks", " ", null);

        assertThat(porDefecto.issuer()).isEqualTo("crediya-auth");
        assertThat(porDefecto.audience()).isEqualTo("crediya");
    }
}
