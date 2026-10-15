package com.crediya.solicitudes.model.solicitud.exceptions;

import com.crediya.solicitudes.model.common.exceptions.DomainException;
import com.crediya.solicitudes.model.solicitud.EstadoSolicitud;

public class TransicionEstadoInvalidaException extends DomainException {

    public static final String CODE = "TRANSICION_ESTADO_INVALIDA";

    public TransicionEstadoInvalidaException(EstadoSolicitud actual, EstadoSolicitud nuevo) {
        super(CODE, "No se puede pasar una solicitud de '" + actual.getDescripcion() + "' a '" + nuevo.getDescripcion() + "'");
    }
}
