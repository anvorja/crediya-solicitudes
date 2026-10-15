package com.crediya.solicitudes.model.solicitud.exceptions;

import com.crediya.solicitudes.model.common.exceptions.DomainException;

public class DatosSolicitudInvalidosException extends DomainException {

    public static final String CODE = "DATOS_SOLICITUD_INVALIDOS";

    private final String campo;

    public DatosSolicitudInvalidosException(String campo, String motivo) {
        super(CODE, "El campo '" + campo + "' " + motivo);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
