package com.crediya.solicitudes.consumer.cliente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

/**
 * Contrato publico del microservicio de AUTENTICACION (GET /api/v1/usuarios/{documento}).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioAutenticacionResponse(
        String documentoIdentidad,
        String nombres,
        String apellidos,
        String correoElectronico,
        BigDecimal salarioBase
) {
}
