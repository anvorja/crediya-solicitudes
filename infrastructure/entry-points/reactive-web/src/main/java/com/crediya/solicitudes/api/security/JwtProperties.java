package com.crediya.solicitudes.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Como validar los tokens que emite AUTENTICACION: solo se necesita su clave publica (JWKS), ningun secreto compartido.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String jwkSetUri, String issuer, String audience) {

    public static final String ISSUER_POR_DEFECTO = "crediya-auth";
    public static final String AUDIENCE_POR_DEFECTO = "crediya";

    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? ISSUER_POR_DEFECTO : issuer;
        audience = audience == null || audience.isBlank() ? AUDIENCE_POR_DEFECTO : audience;
    }
}
