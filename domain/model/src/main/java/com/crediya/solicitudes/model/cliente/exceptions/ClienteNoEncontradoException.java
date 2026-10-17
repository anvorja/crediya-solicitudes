package com.crediya.solicitudes.model.cliente.exceptions;

import com.crediya.solicitudes.model.common.exceptions.DomainException;

public class ClienteNoEncontradoException extends DomainException {

    public static final String CODE = "CLIENTE_NO_ENCONTRADO";

    public ClienteNoEncontradoException() {
        super(CODE, "No existe un cliente registrado con el documento de identidad indicado");
    }
}
