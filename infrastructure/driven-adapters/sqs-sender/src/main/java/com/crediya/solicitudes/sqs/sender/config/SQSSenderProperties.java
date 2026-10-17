package com.crediya.solicitudes.sqs.sender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @param endpoint solo para entornos locales (LocalStack); en AWS se deja vacio
 * @param colas    URL de cada cola de destino, por nombre del {@code Destino} en minusculas (p. ej. {@code notificaciones})
 */
@ConfigurationProperties(prefix = "adapter.sqs")
public record SQSSenderProperties(String region, String endpoint, Map<String, String> colas) {

    public SQSSenderProperties {
        colas = colas == null ? Map.of() : Map.copyOf(colas);
    }
}
