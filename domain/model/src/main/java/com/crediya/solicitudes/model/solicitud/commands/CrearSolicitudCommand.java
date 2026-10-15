package com.crediya.solicitudes.model.solicitud.commands;

import java.math.BigDecimal;

/**
 * Datos crudos de una solicitud de prestamo. Las reglas se validan en el dominio.
 */
public record CrearSolicitudCommand(
        String documentoIdentidad,
        BigDecimal monto,
        Integer plazo,
        String tipoPrestamo
) {
}
