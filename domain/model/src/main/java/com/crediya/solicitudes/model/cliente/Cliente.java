package com.crediya.solicitudes.model.cliente;

import java.math.BigDecimal;

/**
 * Datos del solicitante que pertenecen al servicio de AUTENTICACION y que SOLICITUDES necesita conocer.
 */
public record Cliente(
        String documentoIdentidad,
        String nombres,
        String apellidos,
        String email,
        BigDecimal salarioBase
) {

    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }
}
