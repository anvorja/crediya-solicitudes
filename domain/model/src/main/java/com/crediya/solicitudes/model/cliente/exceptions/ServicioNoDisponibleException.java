package com.crediya.solicitudes.model.cliente.exceptions;

import com.crediya.solicitudes.model.common.exceptions.DomainException;

/**
 * Un servicio del que depende la operacion no responde; el cliente puede reintentar mas tarde.
 */
public class ServicioNoDisponibleException extends DomainException {

    public static final String CODE = "SERVICIO_NO_DISPONIBLE";

    public ServicioNoDisponibleException(String servicio) {
        super(CODE, "El servicio de " + servicio + " no esta disponible en este momento. Intenta de nuevo mas tarde.");
    }
}
