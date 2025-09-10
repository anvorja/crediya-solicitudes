// infrastructure/entry-points/reactive-web/src/main/java/com/crediya/solicitudes/api/dto/request/CrearSolicitudRequest.java
package com.crediya.solicitudes.api.dto.request;

import com.crediya.solicitudes.model.constants.BusinessConstants;
import com.crediya.solicitudes.api.constants.ValidationMessagesConstants;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CrearSolicitudRequest(

        @NotBlank(message = ValidationMessagesConstants.Validation.DOCUMENTO_REQUERIDO)
        @Size(min = BusinessConstants.Documento.LONGITUD_MINIMA,
                max = BusinessConstants.Documento.LONGITUD_MAXIMA,
                message = ValidationMessagesConstants.Validation.DOCUMENTO_LONGITUD)
        @Pattern(regexp = BusinessConstants.Patterns.SOLO_NUMEROS,
                message = ValidationMessagesConstants.Validation.DOCUMENTO_FORMATO)
        String numeroDocumento,

        @NotBlank(message = ValidationMessagesConstants.Validation.NOMBRES_REQUERIDOS)
        @Size(max = BusinessConstants.Texto.NOMBRES_MAX_LENGTH,
                message = ValidationMessagesConstants.Validation.NOMBRES_LONGITUD)
        @Pattern(regexp = BusinessConstants.Patterns.NOMBRES_APELLIDOS,
                message = ValidationMessagesConstants.Validation.NOMBRES_FORMATO)
        String nombres,

        @NotBlank(message = ValidationMessagesConstants.Validation.APELLIDOS_REQUERIDOS)
        @Size(max = BusinessConstants.Texto.APELLIDOS_MAX_LENGTH,
                message = ValidationMessagesConstants.Validation.APELLIDOS_LONGITUD)
        @Pattern(regexp = BusinessConstants.Patterns.NOMBRES_APELLIDOS,
                message = ValidationMessagesConstants.Validation.APELLIDOS_FORMATO)
        String apellidos,

        @NotBlank(message = ValidationMessagesConstants.Validation.EMAIL_REQUERIDO)
        @Email(message = ValidationMessagesConstants.Validation.EMAIL_FORMATO)
        @Size(max = BusinessConstants.Texto.EMAIL_MAX_LENGTH,
                message = ValidationMessagesConstants.Validation.EMAIL_LONGITUD)
        String email,

        @NotBlank(message = ValidationMessagesConstants.Validation.TELEFONO_REQUERIDO)
        @Pattern(regexp = BusinessConstants.Patterns.TELEFONO,
                message = ValidationMessagesConstants.Validation.TELEFONO_FORMATO)
        String telefono,

        @NotNull(message = ValidationMessagesConstants.Validation.MONTO_REQUERIDO)
        @DecimalMin(value = "100000", message = ValidationMessagesConstants.Validation.MONTO_MINIMO)
        @DecimalMax(value = "50000000", message = ValidationMessagesConstants.Validation.MONTO_MAXIMO)
        @Digits(integer = 15, fraction = 2, message = ValidationMessagesConstants.Validation.MONTO_FORMATO)
        BigDecimal montoSolicitado,

        @NotNull(message = ValidationMessagesConstants.Validation.PLAZO_REQUERIDO)
        @Min(value = BusinessConstants.Financiero.PLAZO_MINIMO_MESES,
                message = ValidationMessagesConstants.Validation.PLAZO_MINIMO)
        @Max(value = BusinessConstants.Financiero.PLAZO_MAXIMO_MESES,
                message = ValidationMessagesConstants.Validation.PLAZO_MAXIMO)
        Integer plazoMeses,

        @NotBlank(message = ValidationMessagesConstants.Validation.TIPO_CREDITO_REQUERIDO)
        @Pattern(regexp = BusinessConstants.TipoCredito.TIPOS_REGEX,
                message = ValidationMessagesConstants.Validation.TIPO_CREDITO_INVALIDO)
        String tipoCredito,

        // Campos opcionales para evaluación de capacidad de pago
        @DecimalMin(value = "0", message = ValidationMessagesConstants.Validation.INGRESOS_NEGATIVOS)
        @Digits(integer = 15, fraction = 2, message = ValidationMessagesConstants.Validation.INGRESOS_FORMATO)
        BigDecimal ingresosMensuales,

        @DecimalMin(value = "0", message = ValidationMessagesConstants.Validation.GASTOS_NEGATIVOS)
        @Digits(integer = 15, fraction = 2, message = ValidationMessagesConstants.Validation.GASTOS_FORMATO)
        BigDecimal gastosMensuales
){
    // Records automáticamente generan:
    // - Constructor con todos los parámetros
    // - Métodos accessor (numeroDocumento(), nombres(), etc.)
    // - equals(), hashCode(), toString()
    // - Son inmutables por defecto
    // - Son thread-safe (al no poder modificarse sus campos una vez creados)
}