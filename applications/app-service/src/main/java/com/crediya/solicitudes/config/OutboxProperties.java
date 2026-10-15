package com.crediya.solicitudes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param enabled   apagar el relevo (p. ej. en pruebas que no necesitan colas)
 * @param intervalo cada cuanto se buscan eventos pendientes
 * @param lote      maximo de eventos por ronda
 */
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(Boolean enabled, Duration intervalo, Integer lote) {

    public OutboxProperties {
        enabled = enabled == null || enabled;
        intervalo = intervalo == null ? Duration.ofSeconds(2) : intervalo;
        lote = lote == null || lote < 1 ? 20 : lote;
    }
}
