package com.crediya.solicitudes.api.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * HU4: una fila del listado de revision manual. {@code nombre} y {@code salarioBase} son nulos si AUTENTICACION ya no
 * conoce al solicitante.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SolicitudResumenResponse(
        UUID idSolicitud,
        BigDecimal monto,
        int plazo,
        String email,
        String nombre,
        String tipoPrestamo,
        BigDecimal tasaInteres,
        String estadoSolicitud,
        BigDecimal salarioBase,
        BigDecimal deudaTotalMensualSolicitudesAprobadas
) {
}
