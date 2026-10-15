package com.crediya.solicitudes.api.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SolicitudResponse(
        UUID idSolicitud,
        BigDecimal monto,
        int plazo,
        String email,
        String estado,
        String tipoPrestamo,
        BigDecimal tasaInteres
) {
}
