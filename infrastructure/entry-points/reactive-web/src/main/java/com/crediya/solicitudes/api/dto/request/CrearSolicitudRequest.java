package com.crediya.solicitudes.api.dto.request;

import com.crediya.solicitudes.api.constants.ValidationMessages;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrearSolicitudRequest(
        @NotBlank(message = ValidationMessages.DOCUMENTO_REQUERIDO)
        @Pattern(regexp = "^[0-9]{5,20}$", message = ValidationMessages.DOCUMENTO_FORMATO) String documentoIdentidad,
        @NotNull(message = ValidationMessages.MONTO_REQUERIDO)
        @DecimalMin(value = "0", inclusive = false, message = ValidationMessages.MONTO_POSITIVO) BigDecimal monto,
        @NotNull(message = ValidationMessages.PLAZO_REQUERIDO)
        @Min(value = 1, message = ValidationMessages.PLAZO_MINIMO)
        @Max(value = 360, message = ValidationMessages.PLAZO_MAXIMO) Integer plazo,
        @NotBlank(message = ValidationMessages.TIPO_PRESTAMO_REQUERIDO) String tipoPrestamo
) {
}
