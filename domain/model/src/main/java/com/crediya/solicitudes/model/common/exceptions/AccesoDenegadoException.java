package com.crediya.solicitudes.model.common.exceptions;

public class AccesoDenegadoException extends DomainException {

    public static final String CODE = "ACCESO_DENEGADO";

    public AccesoDenegadoException() {
        super(CODE, "No tienes permisos para realizar esta accion");
    }
}
