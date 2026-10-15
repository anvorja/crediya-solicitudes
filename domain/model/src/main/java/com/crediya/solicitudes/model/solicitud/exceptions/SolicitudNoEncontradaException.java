package com.crediya.solicitudes.model.solicitud.exceptions;

import com.crediya.solicitudes.model.common.exceptions.DomainException;

public class SolicitudNoEncontradaException extends DomainException {

    public static final String CODE = "SOLICITUD_NO_ENCONTRADA";

    public SolicitudNoEncontradaException() {
        super(CODE, "No existe una solicitud con el identificador indicado");
    }
}
