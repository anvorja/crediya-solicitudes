// infrastructure/entry-points/reactive-web/src/main/java/com/crediya/solicitudes/api/dto/response/SolicitudResponse.java
package com.crediya.solicitudes.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de respuesta para solicitudes de préstamo
 * Información que se retorna al cliente después de crear/consultar una solicitud
 */
public record SolicitudResponse(
    String id,
     String numeroDocumento,
     String nombreCompleto,
     String email,
     String telefono,
     BigDecimal montoSolicitado,
     String tipoCredito,

     String estado, // Simplificado: usar string directamente
     String descripcionEstado,

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
     LocalDateTime fechaCreacion,

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
     LocalDateTime fechaActualizacion,

     String observaciones,

     BigDecimal ingresosMensuales,
     BigDecimal gastosMensuales
)
{}