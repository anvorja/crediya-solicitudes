package com.crediya.solicitudes.model.solicitud.exceptions;

import com.crediya.solicitudes.model.common.exceptions.DomainException;

/**
 * Otro proceso modifico la solicitud entre la lectura y la escritura; el cliente debe consultarla de nuevo.
 */
public class SolicitudModificadaException extends DomainException {

    public static final String CODE = "SOLICITUD_MODIFICADA";

    public SolicitudModificadaException() {
        super(CODE, "La solicitud fue modificada por otro usuario. Consultala de nuevo e intenta otra vez");
    }
}
