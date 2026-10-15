package com.crediya.solicitudes.model.solicitud.exceptions;

import com.crediya.solicitudes.model.common.exceptions.DomainException;

public class TipoPrestamoNoValidoException extends DomainException {

    public static final String CODE = "TIPO_PRESTAMO_NO_VALIDO";

    public TipoPrestamoNoValidoException(String tipoPrestamo) {
        super(CODE, "El tipo de prestamo '" + tipoPrestamo + "' no existe");
    }
}
