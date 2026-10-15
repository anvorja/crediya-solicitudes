package com.crediya.solicitudes.model.solicitud;

import java.math.BigDecimal;

/**
 * HU4: una solicitud con los datos del solicitante y su deuda mensual actual, para decidir con contexto.
 * {@code nombreCliente} y {@code salarioBase} son nulos si AUTENTICACION ya no conoce al solicitante.
 */
public record ResumenSolicitud(
        Solicitud solicitud,
        String nombreCliente,
        BigDecimal salarioBase,
        BigDecimal deudaTotalMensualAprobadas
) {
}
