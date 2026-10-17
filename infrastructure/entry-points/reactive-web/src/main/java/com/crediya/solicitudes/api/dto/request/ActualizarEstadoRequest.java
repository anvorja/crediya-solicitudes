package com.crediya.solicitudes.api.dto.request;

import com.crediya.solicitudes.api.constants.ValidationMessages;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ActualizarEstadoRequest(
        @NotBlank(message = ValidationMessages.ID_SOLICITUD_REQUERIDO) String idSolicitud,
        @NotBlank(message = ValidationMessages.ESTADO_REQUERIDO) String estado
) {
}
