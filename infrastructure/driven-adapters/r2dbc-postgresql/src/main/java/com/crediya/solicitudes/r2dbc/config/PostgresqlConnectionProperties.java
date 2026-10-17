package com.crediya.solicitudes.r2dbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexion R2DBC. Sin valores por defecto para credenciales: se inyectan por variables de entorno.
 */
@ConfigurationProperties(prefix = "adapters.r2dbc")
public record PostgresqlConnectionProperties(
        String host,
        Integer port,
        String database,
        String schema,
        String username,
        String password,
        String sslMode,
        String sslRootCert) {
}
