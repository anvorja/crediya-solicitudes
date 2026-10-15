package com.crediya.solicitudes.model.solicitud.exceptions;

import com.crediya.solicitudes.model.common.exceptions.DomainException;

import java.math.BigDecimal;

public class MontoFueraDeRangoException extends DomainException {

    public static final String CODE = "MONTO_FUERA_DE_RANGO";

    public MontoFueraDeRangoException(String tipoPrestamo, BigDecimal minimo, BigDecimal maximo) {
        super(CODE, "El monto solicitado para el tipo de prestamo " + tipoPrestamo
                + " debe estar entre " + minimo.toPlainString() + " y " + maximo.toPlainString());
    }
}
